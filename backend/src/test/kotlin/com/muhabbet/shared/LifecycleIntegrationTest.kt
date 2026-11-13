package com.muhabbet.shared

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.`in`.LinkDeviceUseCase
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.auth.domain.service.AuthService
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.ScheduledDelivery
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageInviteLinkUseCase
import com.muhabbet.messaging.domain.port.`in`.TransferGroupOwnershipUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.messaging.domain.service.MessageService
import com.muhabbet.moderation.domain.model.ReportReason
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReportUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReviewReportsUseCase
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * The database half of the lifecycle fixes in #872, #884, #888, #890 and #891, against a real
 * PostgreSQL built by Flyway from the migrations.
 *
 * The unit tests prove each transition function; what they cannot prove is that the conditional
 * UPDATE behind each one actually serialises concurrent callers, or that a migration's constraint
 * actually holds. Those are claims about PostgreSQL's row locks and indexes, so they are tested
 * against PostgreSQL. One class and one Spring context for all of them, because each context with
 * its two containers is the expensive part.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class LifecycleIntegrationTest {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var deviceRepository: DeviceRepository
    @Autowired private lateinit var conversationRepository: ConversationRepository
    @Autowired private lateinit var messageRepository: MessageRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var transferOwnership: TransferGroupOwnershipUseCase
    @Autowired private lateinit var inviteLinks: ManageInviteLinkUseCase
    @Autowired private lateinit var linkDevice: LinkDeviceUseCase
    @Autowired private lateinit var messageService: MessageService
    @Autowired private lateinit var messageHistory: GetMessageHistoryUseCase
    @Autowired private lateinit var blocks: BlockUserUseCase
    @Autowired private lateinit var reports: ReportUserUseCase
    @Autowired private lateinit var reviews: ReviewReportsUseCase

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("muhabbet_test")
            withUsername("muhabbet")
            withPassword("muhabbet_test")
        }

        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("muhabbet.otp.mock-enabled") { "true" }
            registry.add("spring.data.redis.host") { redis.redisHost }
            registry.add("spring.data.redis.port") { redis.redisPort }
        }
    }

    // ─── #890: exactly one owner ─────────────────────────

    @Test
    fun `should refuse a second OWNER row at the database`() {
        val owner = seedUser()
        val member = seedUser()
        val group = seedGroup(owner, member)

        val ex = assertThrows(SQLException::class.java) {
            dataSource.connection.use { c ->
                c.prepareStatement("UPDATE conversation_members SET role = 'OWNER' WHERE conversation_id = ? AND user_id = ?")
                    .apply { setObject(1, group); setObject(2, member) }
                    .executeUpdate()
            }
        }
        assertEquals("23505", ex.sqlState, "unique violation from uq_conversation_members_single_owner")
    }

    @Test
    fun `should leave exactly one OWNER after a transfer`() {
        val owner = seedUser()
        val member = seedUser()
        val group = seedGroup(owner, member)

        transferOwnership.transferOwnership(group, owner, member)

        val roles = conversationRepository.findMembersByConversationId(group).associate { it.userId to it.role }
        assertEquals(mapOf(owner to MemberRole.ADMIN, member to MemberRole.OWNER), roles)
    }

    // ─── #888: a single-use link admits one person ───────

    @Test
    fun `should admit exactly one of five concurrent joins through a single-use link`() {
        val owner = seedUser()
        val group = seedGroup(owner)
        val link = inviteLinks.createLink(group, owner, requiresApproval = false, maxUses = 1, expiresAt = null)
        val joiners = List(5) { seedUser() }

        val outcomes = concurrently(joiners) { joiner -> inviteLinks.joinViaLink(link.inviteToken, joiner) }

        assertEquals(1, outcomes.count { it == null }, "exactly one join succeeds: $outcomes")
        assertTrue(outcomes.filterNotNull().all { it == ErrorCode.INVITE_LINK_MAX_USES }, "$outcomes")
        assertEquals(2, conversationRepository.findMembersByConversationId(group).size)
        assertEquals(1, queryInt("SELECT use_count FROM group_invite_links WHERE id = ?", link.id))
    }

    // ─── #884: one QR links one device ───────────────────

    @Test
    fun `should link exactly one device when one token is completed concurrently, storing only its hash`() {
        val user = seedUser()
        val primary = deviceRepository.save(Device(userId = user, platform = "android", isPrimary = true))
        val offer = linkDevice.beginLink(user, primary.id)

        val outcomes = concurrently(List(5) { it }) { linkDevice.completeLink(offer.linkToken, "web", "Web $it", null) }

        assertEquals(1, outcomes.count { it == null }, "exactly one completion succeeds: $outcomes")
        assertTrue(outcomes.filterNotNull().all { it == ErrorCode.DEVICE_LINK_ALREADY_USED }, "$outcomes")
        assertEquals(1, deviceRepository.findActiveByUserId(user).count { it.isCompanion })

        dataSource.connection.use { c ->
            c.prepareStatement("SELECT link_token, token_hash, status FROM device_link_sessions WHERE id = ?")
                .apply { setObject(1, offer.session.id) }
                .executeQuery().use { rs ->
                    assertTrue(rs.next())
                    assertNull(rs.getString("link_token"), "the plain token must never be stored")
                    assertEquals(AuthService.sha256(offer.linkToken), rs.getString("token_hash"))
                    assertEquals("COMPLETED", rs.getString("status"))
                }
        }
    }

    @Test
    fun `should return usable credentials to the companion`() {
        val user = seedUser()
        val primary = deviceRepository.save(Device(userId = user, platform = "android", isPrimary = true))
        val offer = linkDevice.beginLink(user, primary.id)

        val completed = linkDevice.completeLink(offer.linkToken, "web", "Web", null)

        assertTrue(completed.tokens.accessToken.isNotBlank())
        assertEquals(
            1,
            queryInt(
                "SELECT count(*) FROM refresh_tokens WHERE device_id = ? AND token_hash = '" +
                    AuthService.sha256(completed.tokens.refreshToken) + "'",
                completed.device.id
            ),
            "the companion's refresh token is stored, hashed, against the companion's device"
        )
    }

    // ─── #872: scheduled delivery ────────────────────────

    @Test
    fun `should never sweep a scheduled message before it is delivered`() {
        val sender = seedUser()
        val group = seedGroup(sender)
        // A row as the old code wrote it: scheduled for next week, with a 24h deadline already due.
        val legacy = saveMessage(
            message(group, sender).copy(
                isScheduled = true,
                scheduledAt = Instant.now().plus(7, ChronoUnit.DAYS),
                expiresAt = Instant.now().minusSeconds(60)
            )
        )

        val expired = transactions.inTransaction { messageRepository.findExpiredMessages(Instant.now(), 1_000) }

        assertFalse(expired.any { it.id == legacy.id }, "a scheduled row is not the sweep's to delete")
    }

    @Test
    fun `should deliver a due scheduled message at delivery time, visible to sync, with its window from delivery`() {
        val sender = seedUser()
        val reader = seedUser()
        val group = seedGroup(sender, reader)
        conversationRepository.findById(group)?.let {
            conversationRepository.updateConversation(it.copy(disappearAfterSeconds = 86_400))
        }
        val writtenAt = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS)
        val scheduled = saveMessage(
            message(group, sender).copy(
                serverTimestamp = writtenAt,
                isScheduled = true,
                scheduledAt = Instant.now().minusSeconds(5)
            )
        )
        val lastSync = Instant.now().minusSeconds(1)

        messageService.deliverScheduledMessages()

        val delivered = messageRepository.findById(scheduled.id) ?: error("message vanished")
        assertFalse(delivered.isScheduled)
        assertTrue(delivered.serverTimestamp.isAfter(writtenAt), "re-stamped at delivery")
        assertEquals(delivered.serverTimestamp.plusSeconds(86_400), delivered.expiresAt)
        assertTrue(
            messageHistory.getMessagesSince(reader, lastSync).any { it.id == scheduled.id },
            "background sync must return a message delivered after the last sync"
        )
    }

    @Test
    fun `should let exactly one of two concurrent claims deliver a scheduled message`() {
        val sender = seedUser()
        val group = seedGroup(sender)
        val scheduled = saveMessage(
            message(group, sender).copy(isScheduled = true, scheduledAt = Instant.now().minusSeconds(5))
        )
        val due = scheduled.deliverDue(Instant.now(), null) as ScheduledDelivery.Due

        val claims = concurrentResults(List(4) { it }) {
            transactions.inTransaction { messageRepository.claimScheduledDelivery(due.message) }
        }

        assertEquals(1, claims.count { it }, "one claim wins: $claims")
    }

    // ─── #891: reports and blocks ────────────────────────

    @Test
    fun `should keep a report's evidence after the sender deletes the message, and decide it once`() {
        val reporter = seedUser()
        val sender = seedUser()
        val chat = seedConversation(ConversationType.DIRECT, reporter, sender)
        val abuse = saveMessage(message(chat, sender).copy(content = "kanıt"))

        val report = reports.reportUser(reporter, null, abuse.id, null, ReportReason.HARASSMENT, null)
        transactions.inTransaction { messageRepository.softDelete(abuse.id) }

        val pending = reviews.getPendingReports(100, 0).single { it.id == report.id }
        assertEquals("kanıt", pending.evidence?.content)
        assertEquals(sender, pending.evidence?.senderId)

        val admin = seedUser()
        reviews.resolveReport(report.id, admin, dismiss = false)
        val again = assertThrows(BusinessException::class.java) { reviews.resolveReport(report.id, admin, dismiss = true) }
        assertEquals(ErrorCode.REPORT_ALREADY_RESOLVED, again.errorCode)
    }

    @Test
    fun `should refuse a report on a message the reporter cannot see`() {
        val sender = seedUser()
        val other = seedUser()
        val chat = seedConversation(ConversationType.DIRECT, sender, other)
        val hidden = saveMessage(message(chat, sender))
        val stranger = seedUser()

        val ex = assertThrows(BusinessException::class.java) {
            reports.reportUser(stranger, null, hidden.id, null, ReportReason.SPAM, null)
        }
        assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
    }

    @Test
    fun `should store one block row for concurrent blocks of the same person`() {
        val blocker = seedUser()
        val blocked = seedUser()

        val outcomes = concurrently(List(6) { it }) { blocks.blockUser(blocker, blocked) }

        assertTrue(outcomes.all { it == null }, "every block call succeeds: $outcomes")
        assertEquals(
            1,
            queryInt("SELECT count(*) FROM user_blocks WHERE blocker_id = ? AND blocked_id = '$blocked'", blocker)
        )
    }

    // ─── helpers ─────────────────────────────────────────

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 is unallocated in Turkey, so a number that leaks reaches nobody.
        userRepository.save(User(id = id, phoneNumber = "+90500" + (1_000_000 + (0..8_999_999).random()), displayName = "u"))
        return id
    }

    private fun seedGroup(owner: UUID, vararg members: UUID): UUID {
        val id = seedConversation(ConversationType.GROUP)
        conversationRepository.saveMember(ConversationMember(conversationId = id, userId = owner, role = MemberRole.OWNER))
        members.forEach { conversationRepository.saveMember(ConversationMember(conversationId = id, userId = it)) }
        return id
    }

    private fun seedConversation(type: ConversationType, vararg members: UUID): UUID {
        val id = UUID.randomUUID()
        conversationRepository.save(Conversation(id = id, type = type, name = if (type == ConversationType.GROUP) "g" else null))
        members.forEach { conversationRepository.saveMember(ConversationMember(conversationId = id, userId = it)) }
        return id
    }

    /** The message out-port requires a caller's transaction (MANDATORY), as the send path has. */
    private fun saveMessage(message: Message): Message = transactions.inTransaction { messageRepository.save(message) }

    private fun message(conversationId: UUID, senderId: UUID) = Message(
        id = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = senderId,
        content = "merhaba",
        clientTimestamp = Instant.now()
    )

    private fun queryInt(sql: String, id: UUID): Int = dataSource.connection.use { c ->
        c.prepareStatement(sql).apply { setObject(1, id) }.executeQuery().use { rs ->
            rs.next()
            rs.getInt(1)
        }
    }

    /** Runs [action] for every input at once; null for a success, else the refusal's error code. */
    private fun <T> concurrently(inputs: List<T>, action: (T) -> Unit): List<ErrorCode?> =
        concurrentResults(inputs) { input ->
            try {
                action(input)
                null
            } catch (e: BusinessException) {
                e.errorCode
            }
        }

    private fun <T, R> concurrentResults(inputs: List<T>, action: (T) -> R): List<R> {
        val pool = Executors.newFixedThreadPool(inputs.size)
        val gate = CountDownLatch(1)
        val futures = inputs.map { input -> pool.submit<R> { gate.await(); action(input) } }
        gate.countDown()
        return try {
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }
    }
}

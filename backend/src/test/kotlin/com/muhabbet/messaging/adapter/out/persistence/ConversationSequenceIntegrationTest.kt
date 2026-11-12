package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.SendResult
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.messaging.domain.service.MessageService
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * #750 and #746 against a real database: the sequence, the replayed ack, and scheduled messages that
 * stay invisible to recipients until they are released.
 *
 * The unit tests pin what the service decides; only Postgres can show the row lock that makes the
 * allocation gapless under concurrency, and only the real JPQL can show that every read path carries
 * the scheduled-row predicate.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ConversationSequenceIntegrationTest {

    @Autowired private lateinit var sendMessageUseCase: SendMessageUseCase
    @Autowired private lateinit var historyUseCase: GetMessageHistoryUseCase
    @Autowired private lateinit var manageMessageUseCase: ManageMessageUseCase
    @Autowired private lateinit var messageService: MessageService
    @Autowired private lateinit var messageRepository: MessageRepository
    @Autowired private lateinit var conversationRepository: ConversationRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var jdbc: JdbcTemplate

    private lateinit var alice: UUID
    private lateinit var bob: UUID
    private lateinit var conversationId: UUID
    private val aliceDevice = UUID.randomUUID()

    companion object {
        private const val CONCURRENT_SENDS = 50
        private const val RETRIED_SENDS = 10

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

    @BeforeEach
    fun setUp() {
        alice = seedUser()
        bob = seedUser()
        conversationId = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = conversationId, type = ConversationType.GROUP, name = "Sıra", createdBy = alice)
        )
        listOf(alice, bob).forEach {
            conversationRepository.saveMember(ConversationMember(conversationId = conversationId, userId = it))
        }
    }

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 prefix is unallocated in Turkey — safe for tests.
        userRepository.save(User(id = id, phoneNumber = "+90500" + (1000000 + (0..8999999).random()), displayName = "u"))
        return id
    }

    private fun send(
        messageId: UUID = UUID.randomUUID(),
        sender: UUID = alice,
        device: UUID? = aliceDevice,
        content: String = "merhaba",
        scheduledAt: Instant? = null
    ): SendResult = sendMessageUseCase.sendMessage(
        SendMessageCommand(
            messageId = messageId,
            conversationId = conversationId,
            senderId = sender,
            content = content,
            clientTimestamp = Instant.now(),
            scheduledAt = scheduledAt,
            senderDeviceId = device
        )
    )

    private fun lastSeq(): Long =
        jdbc.queryForObject("SELECT last_seq FROM conversations WHERE id = ?", Long::class.java, conversationId) ?: -1L

    // ─── #750: sequence ──────────────────────────────────

    @Test
    fun `should hand out gapless unique seq 1 to 50 when 50 senders race on one conversation`() {
        val ids = List(CONCURRENT_SENDS) { UUID.randomUUID() }
        // Ten of the sends are also submitted a second time at the same moment — a client retrying
        // after a lost ack. A retry must replay, never take a number of its own.
        val submissions = ids.map { it to false } + ids.take(RETRIED_SENDS).map { it to true }
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val futures = submissions.map { (id, _) ->
            pool.submit(Callable { start.await(); send(messageId = id) })
        }
        start.countDown()
        val results = futures.map { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()

        val stored = ids.map { messageRepository.findById(it) }
        val seqs = stored.map { it?.seq }
        assertEquals((1L..CONCURRENT_SENDS).toList(), seqs.filterNotNull().sorted(), "seq must be gapless and unique")
        assertEquals(CONCURRENT_SENDS.toLong(), lastSeq())
        assertEquals(RETRIED_SENDS, results.count { it.replay }, "each duplicate submit must be a replay")

        // Every answer — first or replay — carries the seq the row actually holds.
        val seqById = stored.filterNotNull().associate { it.id to it.seq }
        results.forEach { assertEquals(seqById[it.message.id], it.message.seq) }
    }

    @Test
    fun `should replay the original seq and timestamp when the same device resends a message id`() {
        val id = UUID.randomUUID()
        val first = send(messageId = id)
        val second = send(messageId = id)

        assertFalse(first.replay)
        assertTrue(second.replay)
        assertEquals(first.message.seq, second.message.seq)
        assertEquals(
            first.message.serverTimestamp.toEpochMilli(),
            second.message.serverTimestamp.toEpochMilli()
        )
        assertEquals(1L, lastSeq(), "the replay must not allocate")
        assertEquals(
            1,
            jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE id = ?", Int::class.java, id)
        )
        assertEquals(
            aliceDevice,
            jdbc.queryForObject("SELECT sender_device_id FROM messages WHERE id = ?", UUID::class.java, id)
        )
    }

    @Test
    fun `should refuse an id with MSG_DUPLICATE when another member already used it`() {
        val id = UUID.randomUUID()
        send(messageId = id)

        val error = assertThrows(BusinessException::class.java) {
            send(messageId = id, sender = bob, device = UUID.randomUUID())
        }
        assertEquals(ErrorCode.MSG_DUPLICATE, error.errorCode)
    }

    @Test
    fun `should stamp edits and deletes with a later change seq from the same counter`() {
        val sent = send().message
        manageMessageUseCase.editMessage(sent.id, alice, "düzeltildi")
        val edited = messageRepository.findById(sent.id)
        manageMessageUseCase.deleteMessage(sent.id, alice)
        val deleted = messageRepository.findById(sent.id)

        assertEquals(1L, edited?.seq)
        assertEquals(2L, edited?.changeSeq)
        assertEquals(3L, deleted?.changeSeq)
        assertEquals(3L, lastSeq())
    }

    // ─── #746: scheduled messages ────────────────────────

    @Test
    fun `should hide a scheduled message from the recipient and show it to the sender until it is released`() {
        val visible = send(content = "şimdi").message
        val before = Instant.now()
        val scheduled = send(content = "sonra gizli", scheduledAt = Instant.now().plusSeconds(3600)).message
        assertNull(scheduled.seq, "a scheduled message is numbered at release, not when written")

        // History — latest page and a page before a cursor.
        assertEquals(listOf(visible.id), historyIds(bob))
        assertEquals(setOf(visible.id, scheduled.id), historyIds(alice).toSet())
        assertTrue(
            messageRepository.findByConversationId(conversationId, bob, Instant.now().plusSeconds(1), 50)
                .none { it.id == scheduled.id }
        )
        // Background sync.
        assertTrue(historyUseCase.getMessagesSince(bob, before.minusSeconds(60)).none { it.id == scheduled.id })
        assertTrue(historyUseCase.getMessagesSince(alice, before.minusSeconds(60)).any { it.id == scheduled.id })
        // The conversation list's last message.
        assertEquals(visible.id, messageRepository.getLastMessages(listOf(conversationId), bob)[conversationId]?.id)
        assertEquals(visible.id, messageRepository.getLastMessage(conversationId, bob)?.id)
        assertEquals(scheduled.id, messageRepository.getLastMessages(listOf(conversationId), alice)[conversationId]?.id)
        // Search, in the conversation and across all of them.
        assertTrue(messageRepository.searchInConversation(conversationId, bob, "%gizli%", 10, 0).isEmpty())
        assertTrue(messageRepository.searchGlobal(bob, "%gizli%", 10, 0).isEmpty())
        assertEquals(1, messageRepository.searchGlobal(alice, "%gizli%", 10, 0).size)
        // Unread badge.
        assertEquals(1, messageRepository.getUnreadCount(conversationId, bob))
        // Message info.
        val infoError = assertThrows(BusinessException::class.java) { historyUseCase.getMessageInfo(scheduled.id, bob) }
        assertEquals(ErrorCode.MSG_NOT_FOUND, infoError.errorCode)
    }

    @Test
    fun `should hide a scheduled media message from the recipient's shared media until it is released`() {
        val media = transactions.inTransaction {
            messageRepository.save(
                Message(
                    id = UUID.randomUUID(),
                    conversationId = conversationId,
                    senderId = alice,
                    contentType = ContentType.IMAGE,
                    content = "",
                    mediaUrl = "https://media.example/blob.jpg",
                    clientTimestamp = Instant.now(),
                    scheduledAt = Instant.now().plusSeconds(3600),
                    isScheduled = true
                )
            )
        }

        assertEquals(0, messageRepository.countMediaInConversation(conversationId, bob))
        assertTrue(messageRepository.findMediaByConversationId(conversationId, bob, 10, 0).isEmpty())
        assertEquals(1, messageRepository.countMediaInConversation(conversationId, alice))
        assertEquals(media.id, messageRepository.findMediaByConversationId(conversationId, alice, 10, 0).single().id)
    }

    @Test
    fun `should give a released scheduled message the next seq and a timestamp background sync will see`() {
        send(content = "önce")
        val scheduled = send(content = "zamanı geldi", scheduledAt = Instant.now().plusSeconds(3600)).message
        // Bob's device syncs after the message was written but before it is due: it must not see it.
        val bobWatermark = Instant.now()
        assertTrue(historyUseCase.getMessagesSince(bob, bobWatermark).none { it.id == scheduled.id })

        jdbc.update("UPDATE messages SET scheduled_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), scheduled.id)
        messageService.deliverScheduledMessages()

        val released = requireNotNull(messageRepository.findById(scheduled.id))
        assertFalse(released.isScheduled)
        assertEquals(2L, released.seq)
        assertEquals(2L, lastSeq())
        // Before the timestamp moved, this was the message background sync lost for good.
        assertTrue(historyUseCase.getMessagesSince(bob, bobWatermark).any { it.id == scheduled.id })
        assertTrue(historyIds(bob).contains(scheduled.id))
    }

    private fun historyIds(viewer: UUID): List<UUID> =
        historyUseCase.getMessages(conversationId, viewer, null, 50, "before").items.map { it.id }
}

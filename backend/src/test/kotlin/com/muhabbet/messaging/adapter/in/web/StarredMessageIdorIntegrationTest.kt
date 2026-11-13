package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.security.JwtProvider
import com.redis.testcontainers.RedisContainer
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Regression test for #906: starring bypassed membership, so any user could star any message id and
 * read its current content back through `GET /starred`.
 *
 * Drives the real filter chain with minted JWTs and a real Postgres, because both halves of the fix
 * live where a mock cannot see them: the refusal in the service, and the membership join in the
 * read query. Several cases seed bookmark rows straight into the table — a "legacy" star written
 * before the fix, or one that was legitimate when written — to prove the read side does not trust
 * what the write side stored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class StarredMessageIdorIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @Autowired
    private lateinit var transactions: TransactionRunner

    @Autowired
    private lateinit var jdbc: JdbcTemplate

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

    // ─── Seeding ────────────────────────────────────────────

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 prefix is unallocated in Turkey — safe for tests.
        val phone = "+90500" + (1000000 + (0..8999999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }

    private fun bearer(userId: UUID): String =
        "Bearer " + jwtProvider.generateAccessToken(userId, UUID.randomUUID())

    private fun seedGroup(vararg members: UUID): UUID {
        val conversationId = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = conversationId, type = ConversationType.GROUP, name = "g", createdBy = members.first())
        )
        members.forEach {
            conversationRepository.saveMember(
                ConversationMember(conversationId = conversationId, userId = it, role = MemberRole.MEMBER)
            )
        }
        return conversationId
    }

    private fun seedMessage(
        conversationId: UUID,
        sender: UUID,
        viewOnce: Boolean = false,
        isDeleted: Boolean = false,
        isScheduled: Boolean = false
    ): UUID {
        val id = UUID.randomUUID()
        transactions.inTransaction {
            messageRepository.save(
                Message(
                    id = id,
                    conversationId = conversationId,
                    senderId = sender,
                    content = "private body $id",
                    clientTimestamp = Instant.now(),
                    viewOnce = viewOnce,
                    isDeleted = isDeleted,
                    isScheduled = isScheduled,
                    scheduledAt = if (isScheduled) Instant.now().plusSeconds(3600) else null
                )
            )
        }
        return id
    }

    /** A bookmark written directly — as one stored before #906, or before the message changed. */
    private fun seedStarRow(userId: UUID, messageId: UUID) {
        jdbc.update("INSERT INTO starred_messages (user_id, message_id) VALUES (?, ?)", userId, messageId)
    }

    private fun starRows(userId: UUID, messageId: UUID): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM starred_messages WHERE user_id = ? AND message_id = ?",
            Int::class.java,
            userId,
            messageId
        ) ?: 0

    private fun starredIds(userId: UUID, query: String = ""): List<String> {
        val body = mockMvc.perform(get("/api/v1/starred$query").header("Authorization", bearer(userId)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return JsonPath.read(body, "$.data.items[*].id")
    }

    // ─── Star: write side ───────────────────────────────────

    @Test
    fun `should refuse a non-member star with 404 MSG_NOT_FOUND and create no row`() {
        val owner = seedUser()
        val outsider = seedUser()
        val messageId = seedMessage(seedGroup(owner), owner)

        mockMvc.perform(post("/api/v1/starred/$messageId").header("Authorization", bearer(outsider)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("MSG_NOT_FOUND"))

        assertEquals(0, starRows(outsider, messageId))
    }

    @Test
    fun `should answer an unknown message id exactly like a foreign one, not with a 500`() {
        val user = seedUser()

        mockMvc.perform(post("/api/v1/starred/${UUID.randomUUID()}").header("Authorization", bearer(user)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("MSG_NOT_FOUND"))
    }

    @Test
    fun `should refuse a view-once message with MSG_NOT_STARRABLE`() {
        val owner = seedUser()
        val member = seedUser()
        val messageId = seedMessage(seedGroup(owner, member), owner, viewOnce = true)

        mockMvc.perform(post("/api/v1/starred/$messageId").header("Authorization", bearer(member)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("MSG_NOT_STARRABLE"))

        assertEquals(0, starRows(member, messageId))
    }

    @Test
    fun `should hide somebody else's not-yet-due scheduled message as not found`() {
        val owner = seedUser()
        val member = seedUser()
        val messageId = seedMessage(seedGroup(owner, member), owner, isScheduled = true)

        mockMvc.perform(post("/api/v1/starred/$messageId").header("Authorization", bearer(member)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("MSG_NOT_FOUND"))
    }

    @Test
    fun `should write exactly one row when the same star arrives twice at once`() {
        val owner = seedUser()
        val member = seedUser()
        val messageId = seedMessage(seedGroup(owner, member), owner)
        val token = bearer(member)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        val statuses = try {
            val futures = (1..2).map {
                pool.submit<Int> {
                    start.await()
                    mockMvc.perform(post("/api/v1/starred/$messageId").header("Authorization", token))
                        .andReturn().response.status
                }
            }
            start.countDown()
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(listOf(200, 200), statuses)
        assertEquals(1, starRows(member, messageId))
    }

    // ─── GET /starred: read side ────────────────────────────

    @Test
    fun `should stop returning a starred message once the user leaves its conversation`() {
        val owner = seedUser()
        val member = seedUser()
        val conversationId = seedGroup(owner, member)
        val messageId = seedMessage(conversationId, owner)

        mockMvc.perform(post("/api/v1/starred/$messageId").header("Authorization", bearer(member)))
            .andExpect(status().isOk)
        assertEquals(listOf(messageId.toString()), starredIds(member))

        transactions.inTransaction { conversationRepository.removeMember(conversationId, member) }

        assertEquals(emptyList<String>(), starredIds(member))
    }

    @Test
    fun `should not return a legacy star on a conversation the user never belonged to`() {
        val owner = seedUser()
        val outsider = seedUser()
        val messageId = seedMessage(seedGroup(owner), owner)
        seedStarRow(outsider, messageId)

        assertEquals(emptyList<String>(), starredIds(outsider))
    }

    @Test
    fun `should not return a starred message that is now deleted`() {
        val owner = seedUser()
        val member = seedUser()
        val messageId = seedMessage(seedGroup(owner, member), owner, isDeleted = true)
        seedStarRow(member, messageId)

        assertEquals(emptyList<String>(), starredIds(member))
    }

    @Test
    fun `should not return a starred view-once message`() {
        val owner = seedUser()
        val member = seedUser()
        val messageId = seedMessage(seedGroup(owner, member), owner, viewOnce = true)
        seedStarRow(member, messageId)

        assertEquals(emptyList<String>(), starredIds(member))
    }

    @Test
    fun `should not return a starred scheduled message that is not due yet`() {
        val owner = seedUser()
        val member = seedUser()
        val messageId = seedMessage(seedGroup(owner, member), owner, isScheduled = true)
        seedStarRow(member, messageId)

        assertEquals(emptyList<String>(), starredIds(member))
    }

    // ─── Paging ─────────────────────────────────────────────

    @Test
    fun `should reject a limit of zero or above the maximum with 400 VALIDATION_ERROR`() {
        val user = seedUser()

        listOf(0, 101).forEach { limit ->
            mockMvc.perform(get("/api/v1/starred").param("limit", "$limit").header("Authorization", bearer(user)))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }
    }

    @Test
    fun `should reject a malformed cursor with 400 MSG_INVALID_CURSOR`() {
        val user = seedUser()

        mockMvc.perform(get("/api/v1/starred").param("cursor", "nope").header("Authorization", bearer(user)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("MSG_INVALID_CURSOR"))
    }

    @Test
    fun `should order by star time and round-trip the keyset cursor`() {
        val owner = seedUser()
        val member = seedUser()
        val conversationId = seedGroup(owner, member)
        // Sent in this order, starred in the reverse, so send order and star order disagree.
        val sentFirst = seedMessage(conversationId, owner)
        val sentSecond = seedMessage(conversationId, owner)
        val sentThird = seedMessage(conversationId, owner)
        listOf(sentThird, sentSecond, sentFirst).forEach {
            mockMvc.perform(post("/api/v1/starred/$it").header("Authorization", bearer(member)))
                .andExpect(status().isOk)
        }

        val firstBody = mockMvc.perform(
            get("/api/v1/starred").param("limit", "2").header("Authorization", bearer(member))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.hasMore").value(true))
            .andReturn().response.contentAsString
        val firstPage: List<String> = JsonPath.read(firstBody, "$.data.items[*].id")
        val cursor: String = JsonPath.read(firstBody, "$.data.nextCursor")

        assertEquals(listOf(sentFirst, sentSecond).map { it.toString() }, firstPage)

        mockMvc.perform(
            get("/api/v1/starred").param("limit", "2").param("cursor", cursor)
                .header("Authorization", bearer(member))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(sentThird.toString()))
            .andExpect(jsonPath("$.data.hasMore").value(false))
    }
}

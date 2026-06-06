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
import com.muhabbet.shared.security.JwtProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Two-user authorization regression test for the message-search IDOR (Phase 0 / P0-1).
 *
 * Drives the REAL Spring Security filter chain (MockMvc + minted JWTs), not a directly
 * instantiated controller — the bypassed filter chain is exactly why this hole survived CI.
 *
 * User A owns a private conversation with a message. User B is a member of nothing.
 * B's wildcard global search and B's targeted in-conversation search MUST NOT return A's message.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class SearchIdorIntegrationTest {

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

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("muhabbet_test")
            withUsername("muhabbet")
            withPassword("muhabbet_test")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("muhabbet.otp.mock-enabled") { "true" }
            registry.add("spring.data.redis.host") { "localhost" }
            registry.add("spring.data.redis.port") { "6379" }
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
            }
        }
    }

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 prefix is unallocated in Turkey — safe for tests.
        val phone = "+90500" + (1000000 + (0..8999999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }

    private fun bearer(userId: UUID): String =
        "Bearer " + jwtProvider.generateAccessToken(userId, UUID.randomUUID())

    private fun seedConversationOwnedBy(owner: UUID): UUID {
        val conversationId = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = conversationId, type = ConversationType.DIRECT, createdBy = owner)
        )
        conversationRepository.saveMember(
            ConversationMember(conversationId = conversationId, userId = owner, role = MemberRole.OWNER)
        )
        return conversationId
    }

    private fun seedMessage(conversationId: UUID, sender: UUID, marker: String) {
        messageRepository.save(
            Message(
                id = UUID.randomUUID(),
                conversationId = conversationId,
                senderId = sender,
                content = "private body $marker payload",
                clientTimestamp = Instant.now()
            )
        )
    }

    @Test
    fun `user B global search must not return user A private message`() {
        val userA = seedUser()
        val userB = seedUser()
        val conversationId = seedConversationOwnedBy(userA)
        val marker = "marker" + UUID.randomUUID().toString().replace("-", "")
        seedMessage(conversationId, userA, marker)

        // Sanity: A (member) CAN find their own message.
        mockMvc.perform(
            get("/api/v1/search/messages").param("q", marker).header("Authorization", bearer(userA))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(1))

        // IDOR: B (non-member) MUST NOT find A's message via global search.
        mockMvc.perform(
            get("/api/v1/search/messages").param("q", marker).header("Authorization", bearer(userB))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(0))
    }

    @Test
    fun `user B in-conversation search of foreign conversation is rejected`() {
        val userA = seedUser()
        val userB = seedUser()
        val conversationId = seedConversationOwnedBy(userA)
        val marker = "marker" + UUID.randomUUID().toString().replace("-", "")
        seedMessage(conversationId, userA, marker)

        // B targets A's conversation explicitly — must be forbidden (not a member).
        mockMvc.perform(
            get("/api/v1/search/messages")
                .param("q", marker)
                .param("conversationId", conversationId.toString())
                .header("Authorization", bearer(userB))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("MSG_NOT_MEMBER"))
    }
}

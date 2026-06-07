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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Two-user authorization regression for the message IDORs (Phase 1 / findings #1 + #2).
 *
 * Drives the REAL Spring Security filter chain (MockMvc + minted JWTs), not a directly
 * instantiated controller — mirrors SearchIdorIntegrationTest.
 *
 * #1 getMessageInfo: a non-member who knows the messageId must NOT read content/sender/recipients.
 * #2 markViewOnceViewed: a non-member must NOT be able to burn a view-once message.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MessageIdorIntegrationTest {

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

    private fun seedMessage(conversationId: UUID, sender: UUID, viewOnce: Boolean = false): UUID {
        val id = UUID.randomUUID()
        messageRepository.save(
            Message(
                id = id,
                conversationId = conversationId,
                senderId = sender,
                content = "private body payload",
                viewOnce = viewOnce,
                clientTimestamp = Instant.now()
            )
        )
        return id
    }

    @Test
    fun `member can read message info but non-member is forbidden`() {
        val userA = seedUser()
        val userB = seedUser()
        val conversationId = seedConversationOwnedBy(userA)
        val messageId = seedMessage(conversationId, userA)

        // Member (A) CAN read the info.
        mockMvc.perform(
            get("/api/v1/messages/$messageId/info").header("Authorization", bearer(userA))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.messageId").value(messageId.toString()))

        // IDOR: non-member B must be forbidden — no content/sender/recipients leak.
        mockMvc.perform(
            get("/api/v1/messages/$messageId/info").header("Authorization", bearer(userB))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("MSG_NOT_MEMBER"))
    }

    @Test
    fun `non-member cannot burn a view-once message`() {
        val userA = seedUser()
        val userB = seedUser()
        val conversationId = seedConversationOwnedBy(userA)
        val messageId = seedMessage(conversationId, userA, viewOnce = true)

        // IDOR: non-member B tries to burn the view-once — must be forbidden.
        mockMvc.perform(
            post("/api/v1/messages/$messageId/view-once").header("Authorization", bearer(userB))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("MSG_NOT_MEMBER"))

        // And it must NOT have been burned — the message is still un-viewed.
        val msg = messageRepository.findById(messageId)
        assert(msg != null && msg.viewedAt == null) { "view-once was burned by a non-member" }
    }
}

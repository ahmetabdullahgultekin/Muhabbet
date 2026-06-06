package com.muhabbet.media.adapter.`in`.web

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.media.domain.model.MediaFile
import com.muhabbet.media.domain.port.out.MediaFileRepository
import com.muhabbet.media.domain.port.out.MediaStoragePort
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
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
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/**
 * Two-user authorization regression test for the media presigned-URL IDOR (Phase 0 / P0-5/P0-8/P0-13).
 *
 * Drives the REAL Spring Security filter chain (MockMvc + minted JWTs). The MinIO storage port is
 * replaced by a no-op stub (@Primary @TestConfiguration bean) so the test never reaches a real object
 * store; the only behaviour under test is the ownership/membership gate that decides whether a
 * presigned URL is issued at all.
 *
 * User A uploads media and references it in a private conversation A belongs to. User B is also a
 * member; an outsider is a member of nothing. The outsider requesting A's media MUST get 403; the
 * uploader and any conversation member MUST get 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MediaIdorIntegrationTest {

    @TestConfiguration
    class StubStorageConfig {
        @Bean
        @Primary
        fun stubMediaStoragePort(): MediaStoragePort = object : MediaStoragePort {
            override fun putObject(key: String, inputStream: InputStream, contentType: String, sizeBytes: Long) {}
            override fun getPresignedUrl(key: String, expirySeconds: Int): String = "https://media.example/$key"
            override fun deleteObject(key: String) {}
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var mediaFileRepository: MediaFileRepository

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
        val phone = "+90500" + (1000000 + (0..8999999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }

    private fun bearer(userId: UUID): String =
        "Bearer " + jwtProvider.generateAccessToken(userId, UUID.randomUUID())

    @Test
    fun `presigned url issued to uploader and conversation member but not to outsider`() {
        val userA = seedUser() // uploader + member
        val userB = seedUser() // member of the conversation
        val outsider = seedUser() // member of nothing

        val mediaId = UUID.randomUUID()
        val fileKey = "images/$userA/$mediaId.jpg"
        mediaFileRepository.save(
            MediaFile(
                id = mediaId,
                uploaderId = userA,
                fileKey = fileKey,
                contentType = "image/jpeg",
                sizeBytes = 123L
            )
        )

        val conversationId = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = conversationId, type = ConversationType.DIRECT, createdBy = userA)
        )
        conversationRepository.saveMember(
            ConversationMember(conversationId = conversationId, userId = userA, role = MemberRole.OWNER)
        )
        conversationRepository.saveMember(
            ConversationMember(conversationId = conversationId, userId = userB, role = MemberRole.MEMBER)
        )
        // The message references the media via its stored URL (contains the file_key).
        messageRepository.save(
            Message(
                id = UUID.randomUUID(),
                conversationId = conversationId,
                senderId = userA,
                contentType = com.muhabbet.messaging.domain.model.ContentType.IMAGE,
                content = "",
                mediaUrl = "https://media.example/muhabbet-media/$fileKey?X-Amz-Signature=abc",
                clientTimestamp = Instant.now()
            )
        )

        // Uploader → 200
        mockMvc.perform(get("/api/v1/media/$mediaId/url").header("Authorization", bearer(userA)))
            .andExpect(status().isOk)

        // Conversation member → 200
        mockMvc.perform(get("/api/v1/media/$mediaId/url").header("Authorization", bearer(userB)))
            .andExpect(status().isOk)

        // Outsider → 403 (IDOR closed)
        mockMvc.perform(get("/api/v1/media/$mediaId/url").header("Authorization", bearer(outsider)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("MEDIA_FORBIDDEN"))
    }
}

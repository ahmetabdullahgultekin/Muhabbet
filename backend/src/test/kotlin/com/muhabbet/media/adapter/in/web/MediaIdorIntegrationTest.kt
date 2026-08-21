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
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.security.JwtProvider
import com.redis.testcontainers.RedisContainer
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
 * Authorization regression test for the media presigned-URL endpoint (#267, and Phase 0 P0-5/P0-8/P0-13
 * before it).
 *
 * Drives the REAL Spring Security filter chain (MockMvc + minted JWTs). The MinIO storage port is
 * replaced by a no-op stub (@Primary @TestConfiguration bean) so the test never reaches a real object
 * store; the only behaviour under test is the gate deciding whether a presigned URL is issued at all.
 *
 * **Minting is uploader-only.** It used to also accept "you belong to a conversation holding a message
 * that references this file" — but that message's `media_url` is written from the request body, so the
 * requester could author the evidence. The second test here is that exploit: an attacker points a
 * message in their own conversation at a victim's file and still gets 403.
 *
 * The consequence, asserted deliberately: a genuine conversation member who did not upload the file
 * also gets 403. That is a real capability removal, not an oversight. Nothing in the product calls this
 * endpoint — media renders from the presigned URL already stored on the message — so no user-facing
 * behaviour depends on it. Restoring member access needs a server-resolved media id, not a
 * client-supplied string.
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
            override fun resolveObjectKey(url: String): String? =
                url.removePrefix("https://media.example/").takeIf { it != url && it.isNotBlank() }
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

    /**
     * Seeding a message needs a transaction the same way the send path does: since #492 the
     * adapter's insert is a bare `entityManager.persist`, declared `MANDATORY`, so it no longer
     * brings a transaction of its own the way `SimpleJpaRepository.save` did. This is the same
     * seam production uses — [TransactionRunner] — rather than `@Transactional` on the test, which
     * would roll the seed back and let the request under test read it from the same transaction.
     */
    @Autowired
    private lateinit var transactions: TransactionRunner

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

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        val phone = "+90500" + (1000000 + (0..8999999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }

    private fun bearer(userId: UUID): String =
        "Bearer " + jwtProvider.generateAccessToken(userId, UUID.randomUUID())

    @Test
    fun `presigned url is issued to the uploader and to nobody else`() {
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
        transactions.inTransaction {
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
        }

        // Uploader → 200
        mockMvc.perform(get("/api/v1/media/$mediaId/url").header("Authorization", bearer(userA)))
            .andExpect(status().isOk)

        // Conversation member → 403.
        //
        // This asserted 200 until #267. Membership was established by a message whose media_url came
        // straight from the request body, so "a conversation you belong to references this file" was a
        // claim the requester could manufacture — userB could have sent themselves this exact message
        // about userA's file and passed the same check. Minting is now uploader-only.
        mockMvc.perform(get("/api/v1/media/$mediaId/url").header("Authorization", bearer(userB)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("MEDIA_FORBIDDEN"))

        // Outsider → 403
        mockMvc.perform(get("/api/v1/media/$mediaId/url").header("Authorization", bearer(outsider)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("MEDIA_FORBIDDEN"))
    }

    @Test
    fun `a self-authored message naming someone elses media grants no access`() {
        val victim = seedUser()
        val attacker = seedUser()

        val mediaId = UUID.randomUUID()
        val fileKey = "images/$victim/$mediaId.jpg"
        mediaFileRepository.save(
            MediaFile(
                id = mediaId,
                uploaderId = victim,
                fileKey = fileKey,
                contentType = "image/jpeg",
                sizeBytes = 123L
            )
        )

        // The attacker sets up exactly the evidence the old check looked for: a conversation they
        // belong to, holding a message that references the victim's file.
        val conversationId = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = conversationId, type = ConversationType.DIRECT, createdBy = attacker)
        )
        conversationRepository.saveMember(
            ConversationMember(conversationId = conversationId, userId = attacker, role = MemberRole.OWNER)
        )
        transactions.inTransaction {
            messageRepository.save(
                Message(
                    id = UUID.randomUUID(),
                    conversationId = conversationId,
                    senderId = attacker,
                    contentType = com.muhabbet.messaging.domain.model.ContentType.IMAGE,
                    content = "",
                    mediaUrl = "https://media.example/muhabbet-media/$fileKey?X-Amz-Signature=abc",
                    clientTimestamp = Instant.now()
                )
            )
        }

        mockMvc.perform(get("/api/v1/media/$mediaId/url").header("Authorization", bearer(attacker)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("MEDIA_FORBIDDEN"))
    }
}

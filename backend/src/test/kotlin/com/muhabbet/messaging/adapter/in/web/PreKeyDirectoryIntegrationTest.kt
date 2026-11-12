package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.OneTimePreKey
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.EncryptionKeyRepository
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import com.muhabbet.shared.security.JwtProvider
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The one-time prekey directory under abuse (#749, ADR 0018 §3, RQ-11, T-KEY-01/02): concurrent
 * claims never hand out the same key, an unrelated or blocked requester is refused, and a single
 * pair is rate-limited. Real Postgres for the `FOR UPDATE SKIP LOCKED` claim, real Redis for the
 * budget, real security chain for the requester identity. The per-pair budget is lowered to
 * [PAIR_BUDGET] so the test can spend it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class PreKeyDirectoryIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var encryptionKeyRepository: EncryptionKeyRepository

    @Autowired
    private lateinit var blockUserUseCase: BlockUserUseCase

    companion object {
        private const val PAIR_BUDGET = 3
        private const val PREKEYS = 20
        private const val CLAIMERS = 30

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
            registry.add("muhabbet.encryption.prekey-fetch.max-per-day-per-pair") { PAIR_BUDGET.toString() }
        }
    }

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 prefix is unallocated in Turkey — safe for tests.
        val phone = "+90500" + (1000000 + (0..8999999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }

    private fun seedKeys(userId: UUID, preKeys: Int) {
        encryptionKeyRepository.saveKeyBundle(
            userId,
            EncryptionKeyBundle(
                userId = userId,
                identityKey = "identity-$userId",
                signedPreKey = "signed-$userId",
                signedPreKeySignature = "sig-$userId",
                signedPreKeyId = 1,
                registrationId = 1
            )
        )
        encryptionKeyRepository.saveOneTimePreKeys(
            userId,
            (1..preKeys).map { OneTimePreKey(userId = userId, keyId = it, publicKey = "otpk-$userId-$it") }
        )
    }

    private fun seedDirectConversation(a: UUID, b: UUID) {
        val conversationId = UUID.randomUUID()
        conversationRepository.save(Conversation(id = conversationId, type = ConversationType.DIRECT, createdBy = a))
        conversationRepository.saveMember(ConversationMember(conversationId = conversationId, userId = a, role = MemberRole.OWNER))
        conversationRepository.saveMember(ConversationMember(conversationId = conversationId, userId = b, role = MemberRole.MEMBER))
    }

    private fun fetchBundle(requester: UUID, target: UUID): ResultActions =
        mockMvc.perform(
            get("/api/v1/encryption/bundle/$target")
                .header("Authorization", "Bearer " + jwtProvider.generateAccessToken(requester, UUID.randomUUID()))
        )

    @Test
    fun `should never hand the same one-time prekey to two concurrent claimers`() {
        val target = seedUser()
        seedKeys(target, PREKEYS)
        val pool = Executors.newFixedThreadPool(CLAIMERS)
        val startGate = CountDownLatch(1)

        val claims = (1..CLAIMERS).map {
            pool.submit<Int?> {
                startGate.await()
                encryptionKeyRepository.consumeOneTimePreKey(target)?.keyId
            }
        }
        startGate.countDown()
        val claimed = claims.mapNotNull { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(claimed.size, claimed.toSet().size, "a one-time prekey was handed out twice: $claimed")
        assertEquals((1..PREKEYS).toSet(), claimed.toSet())
        assertEquals(0, encryptionKeyRepository.countUnusedPreKeys(target))
    }

    @Test
    fun `should refuse a requester who shares no conversation with the target and leave the prekeys alone`() {
        val stranger = seedUser()
        val target = seedUser()
        seedKeys(target, 2)

        fetchBundle(stranger, target)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("ENCRYPTION_KEY_BUNDLE_FORBIDDEN"))

        assertEquals(2, encryptionKeyRepository.countUnusedPreKeys(target))
    }

    @Test
    fun `should serve a contact and refuse them once the target blocks them`() {
        val requester = seedUser()
        val target = seedUser()
        seedKeys(target, 2)
        seedDirectConversation(requester, target)

        fetchBundle(requester, target)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.oneTimePreKey").isNotEmpty)

        blockUserUseCase.blockUser(target, requester)

        fetchBundle(requester, target)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("ENCRYPTION_KEY_BUNDLE_FORBIDDEN"))
        assertEquals(1, encryptionKeyRepository.countUnusedPreKeys(target))
    }

    @Test
    fun `should rate-limit one requester draining one target`() {
        val requester = seedUser()
        val target = seedUser()
        seedKeys(target, PAIR_BUDGET + 5)
        seedDirectConversation(requester, target)

        repeat(PAIR_BUDGET) { fetchBundle(requester, target).andExpect(status().isOk) }

        fetchBundle(requester, target)
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("ENCRYPTION_KEY_BUNDLE_RATE_LIMITED"))
        assertEquals(5, encryptionKeyRepository.countUnusedPreKeys(target))
    }
}

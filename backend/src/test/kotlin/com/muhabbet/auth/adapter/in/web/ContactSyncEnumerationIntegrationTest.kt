package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import com.muhabbet.shared.security.JwtProvider
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * RQ-12 / T-DIR-01 (#748): enumeration through `/api/v1/contacts/sync` is throttled per account,
 * end to end — real security chain, real Redis script, real Postgres. The budget is lowered to
 * [DAILY_BUDGET] so the test can spend it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ContactSyncEnumerationIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var phoneHashRepository: PhoneHashRepository

    @Autowired
    private lateinit var blockUserUseCase: BlockUserUseCase

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    companion object {
        private const val DAILY_BUDGET = 10

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
            registry.add("muhabbet.contact-sync.max-new-hashes-per-day") { DAILY_BUDGET.toString() }
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

    private fun sync(requester: UUID, hashes: List<String>): ResultActions {
        val body = hashes.joinToString(prefix = """{"phoneHashes":[""", postfix = "]}") { "\"$it\"" }
        return mockMvc.perform(
            post("/api/v1/contacts/sync")
                .header("Authorization", bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
    }

    @Test
    fun `should throttle a walk through the number space once the daily budget is spent`() {
        val scanner = seedUser()
        val firstPage = (1..DAILY_BUDGET).map { "scan-$scanner-$it" }

        sync(scanner, firstPage).andExpect(status().isOk)

        sync(scanner, listOf("scan-$scanner-next"))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("CONTACT_SYNC_DAILY_LIMIT_EXCEEDED"))

        // An address book already looked up today is still served: re-syncing is free.
        sync(scanner, firstPage).andExpect(status().isOk)
    }

    @Test
    fun `should not show a user who has blocked the requester`() {
        val requester = seedUser()
        val friend = seedUser()
        val blocker = seedUser()
        phoneHashRepository.save(friend, "hash-$friend")
        phoneHashRepository.save(blocker, "hash-$blocker")
        blockUserUseCase.blockUser(blocker, requester)

        sync(requester, listOf("hash-$friend", "hash-$blocker"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.matchedContacts.length()").value(1))
            .andExpect(jsonPath("$.data.matchedContacts[0].userId").value(friend.toString()))
    }

    @Test
    fun `should keep only keyed digests in Redis and let them expire with the day when contacts are synced`() {
        val requester = seedUser()
        val hashes = (1..3).map { "addressbook-$requester-$it" }

        sync(requester, hashes).andExpect(status().isOk)

        val key = redisTemplate.keys("contacts:quota:$requester:*").single()
        val stored = redisTemplate.opsForSet().members(key).orEmpty()
        assertEquals(hashes.size, stored.size)
        hashes.forEach { assertFalse(it in stored, "raw phone hash $it was retained in Redis") }
        stored.forEach { assertEquals(DIGEST_HEX_LENGTH, it.length, "digest $it is not truncated to 12 bytes") }
        val ttl = redisTemplate.getExpire(key)
        assertTrue(ttl in 1..MAX_TTL_SECONDS, "TTL $ttl outlives the UTC day plus margin")
    }
}

private const val DIGEST_HEX_LENGTH = 24
private const val MAX_TTL_SECONDS = 25L * 3600

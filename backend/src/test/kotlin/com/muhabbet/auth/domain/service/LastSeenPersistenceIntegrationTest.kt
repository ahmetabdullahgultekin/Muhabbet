package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.`in`.RecordLastSeenUseCase
import com.muhabbet.auth.domain.port.out.UserRepository
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertNotNull
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
import java.time.Instant
import java.util.UUID

/**
 * #402, at the level the issue asked for: the row must actually move.
 *
 * The production log said `No active transaction for update or delete query` on every disconnect,
 * so `users.last_seen_at` had never once been written from the socket lifecycle. The verification
 * the issue asked for is a row, not a green mock — and deliberately so, because every mock-based
 * test of this path passed while the column stayed null.
 *
 * Note what this test does **not** do: it does not annotate itself `@Transactional`. That is the
 * whole point. A test that opens its own transaction supplies the very thing that was missing in
 * production and would pass against the broken code.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class LastSeenPersistenceIntegrationTest {

    @Autowired private lateinit var recordLastSeen: RecordLastSeenUseCase
    @Autowired private lateinit var userRepository: UserRepository

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

    @Test
    fun `should persist last seen when called with no ambient transaction`() {
        val user = userRepository.save(
            User(
                id = UUID.randomUUID(),
                phoneNumber = "+9050000${(10000..99999).random()}",
                displayName = "Last Seen Test",
                status = UserStatus.ACTIVE,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        val at = Instant.now()

        recordLastSeen.recordLastSeen(user.id, at)

        val reloaded = userRepository.findById(user.id)
        assertNotNull(reloaded?.lastSeenAt, "last_seen_at is still null — the write did not land (#402)")
        val stored = reloaded?.lastSeenAt ?: Instant.EPOCH
        assertTrue(
            Math.abs(stored.toEpochMilli() - at.toEpochMilli()) < 1000,
            "last_seen_at is $stored, expected roughly $at"
        )
    }
}

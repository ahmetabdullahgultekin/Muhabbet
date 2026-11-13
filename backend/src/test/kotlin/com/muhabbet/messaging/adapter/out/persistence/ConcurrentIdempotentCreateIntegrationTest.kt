package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Creation paths whose idempotency used to rest on a read before the write (#899). Two users
 * opening a chat with each other at once, or a double tap on Block, both passed the read, and the
 * second insert hit the unique key as a 500. Only a real database decides these races, so they run
 * here, with every request released at the same instant.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ConcurrentIdempotentCreateIntegrationTest {

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
            registry.add("spring.data.redis.host") { redis.redisHost }
            registry.add("spring.data.redis.port") { redis.redisPort }
        }
    }

    @Autowired private lateinit var createConversation: CreateConversationUseCase
    @Autowired private lateinit var blockUser: BlockUserUseCase
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbc: JdbcTemplate

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        userRepository.save(User(id = id, phoneNumber = "+90500" + (1_000_000..8_999_999).random()))
        return id
    }

    /** Runs [times] copies of [action] released by one latch; returns each outcome. */
    private fun <T> race(times: Int, action: (Int) -> T): List<Result<T>> {
        val pool = Executors.newFixedThreadPool(times)
        val start = CountDownLatch(1)
        val futures = (0 until times).map { i ->
            pool.submit<Result<T>> {
                start.await()
                runCatching { action(i) }
            }
        }
        start.countDown()
        return futures.map { it.get(60, TimeUnit.SECONDS) }.also { pool.shutdown() }
    }

    @Test
    fun `should give ten concurrent direct-chat creates for one pair the same conversation`() {
        val alice = seedUser()
        val bob = seedUser()

        // Both directions at once: each side opening a chat with the other.
        val results = race(10) { i ->
            if (i % 2 == 0) createConversation.createConversation(ConversationType.DIRECT, alice, listOf(bob), null)
            else createConversation.createConversation(ConversationType.DIRECT, bob, listOf(alice), null)
        }

        val failures = results.mapNotNull { it.exceptionOrNull() }
        assertTrue(failures.isEmpty(), "a concurrent create failed: $failures")
        val ids = results.map { it.getOrThrow().conversation.id }.toSet()
        assertEquals(1, ids.size, "the pair got more than one conversation")
        assertEquals(2, results.first().getOrThrow().members.size)

        val (low, high) = listOf(alice, bob).sortedBy { it.toString() }
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM direct_conversation_lookup WHERE user_id_low = ? AND user_id_high = ?",
                Int::class.java, low, high
            )
        )
        // No orphan: the losers' discarded rows are gone, so exactly one DIRECT conversation has
        // either of them as its creator.
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM conversations WHERE type = 'DIRECT' AND created_by IN (?, ?)",
                Int::class.java, alice, bob
            )
        )
    }

    @Test
    fun `should accept a concurrent double block and keep one row`() {
        val blocker = seedUser()
        val blocked = seedUser()

        val results = race(2) { blockUser.blockUser(blocker, blocked) }

        assertTrue(results.all { it.isSuccess }, "a double tap on Block failed: ${results.mapNotNull { it.exceptionOrNull() }}")
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM user_blocks WHERE blocker_id = ? AND blocked_id = ?",
                Int::class.java, blocker, blocked
            )
        )
    }
}

package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Status
import com.muhabbet.messaging.domain.port.out.StatusRepository
import com.muhabbet.messaging.domain.port.out.StatusViewRepository
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/**
 * The persistence leg of #787 against a real PostgreSQL: `V28__status_views.sql` applies, the
 * entity validates against it (`ddl-auto: validate`), a repeat view is a no-op on the unique
 * constraint rather than a second row or an exception, and a deleted status takes its views with it.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class StatusViewPersistenceIntegrationTest {

    @Autowired private lateinit var statusViewRepository: StatusViewRepository
    @Autowired private lateinit var statusRepository: StatusRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var transactions: TransactionTemplate

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

    private fun rowsFor(statusId: UUID): Int =
        JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM status_views WHERE status_id = ?", Int::class.java, statusId
        ) ?: 0

    @Test
    fun `should record a view once however many times it is reported`() {
        val author = seedUser()
        val viewer = seedUser()
        val status = statusRepository.save(Status(userId = author, content = "Merhaba"))

        repeat(3) {
            transactions.executeWithoutResult { statusViewRepository.recordView(status.id, viewer, Instant.now()) }
        }

        assertEquals(1, rowsFor(status.id))
        assertEquals(setOf(status.id), statusViewRepository.findViewedStatusIds(viewer, listOf(status.id)))
    }

    @Test
    fun `should report only the statuses this viewer has seen`() {
        val author = seedUser()
        val viewer = seedUser()
        val other = seedUser()
        val seen = statusRepository.save(Status(userId = author, content = "1"))
        val unseen = statusRepository.save(Status(userId = author, content = "2"))
        transactions.executeWithoutResult { statusViewRepository.recordView(seen.id, viewer, Instant.now()) }
        transactions.executeWithoutResult { statusViewRepository.recordView(unseen.id, other, Instant.now()) }

        assertEquals(setOf(seen.id), statusViewRepository.findViewedStatusIds(viewer, listOf(seen.id, unseen.id)))
        assertEquals(emptySet<UUID>(), statusViewRepository.findViewedStatusIds(viewer, emptyList()))
    }

    @Test
    fun `should drop the views when the status is deleted`() {
        val author = seedUser()
        val viewer = seedUser()
        val status = statusRepository.save(Status(userId = author, content = "gider"))
        transactions.executeWithoutResult { statusViewRepository.recordView(status.id, viewer, Instant.now()) }

        transactions.executeWithoutResult { statusRepository.delete(status.id) }

        assertEquals(0, rowsFor(status.id))
    }

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 is unallocated in Turkey, so a number that leaks reaches nobody.
        val phone = "+90500" + (1_000_000 + (0..8_999_999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }
}

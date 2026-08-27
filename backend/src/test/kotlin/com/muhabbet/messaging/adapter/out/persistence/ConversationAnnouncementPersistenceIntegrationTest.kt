package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import javax.sql.DataSource

/**
 * The persistence leg of #509, against a real PostgreSQL rather than a mock.
 *
 * `conversations.announcement_only` has existed since `V16__whatsapp_feature_parity.sql`, so this
 * fix needed no new migration — but nothing in the suite had ever written that column to a database
 * and read it back, which meant "the column exists" rested on reading a `.sql` file. Here Flyway
 * builds the schema from the migrations and the out-port round-trips the flag through it, so the
 * claim is executed rather than asserted from a diff.
 *
 * Redis runs alongside Postgres for the same reason as every other integration class here:
 * `RedisConfig` registers a listener container the `test` profile does not exclude, and the Spring
 * context will not start without a reachable one.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ConversationAnnouncementPersistenceIntegrationTest {

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var creatorId: UUID

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

    @BeforeEach
    fun setUp() {
        creatorId = seedUser()
    }

    /**
     * Reads the migrated schema rather than the migration file. Flyway builds this database from
     * `db/migration` on context startup, so if `V16` had not applied — or the column had been
     * renamed by a later one — this fails here instead of somewhere downstream. It is also the
     * check that says a new migration was not needed for #509: the column is already there.
     */
    @Test
    fun `should have announcement_only on conversations after the migrations run`() {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_name = 'conversations' AND column_name = 'announcement_only'
                """.trimIndent()
            ).executeQuery().use { rows ->
                assertTrue(rows.next(), "conversations.announcement_only is missing from the migrated schema")
                assertEquals("boolean", rows.getString("data_type"))
                assertEquals("NO", rows.getString("is_nullable"))
                assertEquals("false", rows.getString("column_default"))
            }
        }
    }

    @Test
    fun `should record V16 as an applied migration`() {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT success FROM flyway_schema_history WHERE version = '16'"
            ).executeQuery().use { rows ->
                assertTrue(rows.next(), "V16 was never applied to this database")
                assertTrue(rows.getBoolean("success"))
            }
        }
    }

    @Test
    fun `should default a new group to open posting`() {
        val id = seedGroupConversation()

        assertFalse(conversationRepository.findById(id)?.announcementOnly ?: true)
    }

    @Test
    fun `should persist announcement mode across a reload`() {
        val id = seedGroupConversation()
        val stored = conversationRepository.findById(id)
            ?: error("seeded conversation went missing")

        conversationRepository.updateConversation(stored.copy(announcementOnly = true))

        assertTrue(conversationRepository.findById(id)?.announcementOnly ?: false)
    }

    @Test
    fun `should persist announcement mode being turned back off`() {
        val id = seedGroupConversation()
        val stored = conversationRepository.findById(id)
            ?: error("seeded conversation went missing")
        conversationRepository.updateConversation(stored.copy(announcementOnly = true))

        val announcementOnly = conversationRepository.findById(id)
            ?: error("conversation went missing")
        conversationRepository.updateConversation(announcementOnly.copy(announcementOnly = false))

        assertFalse(conversationRepository.findById(id)?.announcementOnly ?: true)
    }

    /**
     * The update path rewrites several columns at once. A flag that survives on its own but gets
     * clobbered when the group is also renamed would still lose the group its protection.
     */
    @Test
    fun `should keep announcement mode when other group fields are updated`() {
        val id = seedGroupConversation()
        val stored = conversationRepository.findById(id)
            ?: error("seeded conversation went missing")
        conversationRepository.updateConversation(stored.copy(announcementOnly = true))

        val announcementOnly = conversationRepository.findById(id)
            ?: error("conversation went missing")
        conversationRepository.updateConversation(announcementOnly.copy(name = "Yeni ad"))

        val reloaded = conversationRepository.findById(id)
        assertTrue(reloaded?.announcementOnly ?: false)
    }

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 is unallocated in Turkey, so a number that leaks reaches nobody.
        val phone = "+90500" + (1_000_000 + (0..8_999_999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }

    private fun seedGroupConversation(): UUID {
        val id = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = id, type = ConversationType.GROUP, name = "Bahçe Katı", createdBy = creatorId)
        )
        return id
    }
}

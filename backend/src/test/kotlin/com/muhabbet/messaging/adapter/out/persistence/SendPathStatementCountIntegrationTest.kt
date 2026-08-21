package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.redis.testcontainers.RedisContainer
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.time.Instant
import java.util.UUID

/**
 * #492 — the statement counts themselves, which is the only place the fix is actually observable.
 *
 * `SimpleJpaRepository.save` decided between `persist` and `merge` on "is the id null", and every id
 * here is assigned, so every insert on the send path was a merge: a SELECT for the row, always
 * empty, before the INSERT. One for the message and one per recipient. Those interleaved reads also
 * defeated `hibernate.jdbc.batch_size` — a batch cannot form when each statement has to round-trip
 * before the next is issued.
 *
 * The unit tests next door assert the *decision* (the adapter calls `persist`, not `save`). Only a
 * real database can show the consequence, so this needs Testcontainers and therefore runs on CI, not
 * on a developer machine without Docker — where it will report `initializationError` along with the
 * other integration classes. That is expected there and is not a pass.
 *
 * Redis runs alongside Postgres because `RedisConfig` registers a message-listener container the
 * `test` profile does not exclude, so the context will not start without one.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SendPathStatementCountIntegrationTest {

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var transactions: TransactionRunner

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private lateinit var senderId: UUID
    private lateinit var conversationId: UUID

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
            // Off everywhere else — it is not free — and the only thing that can answer the
            // question this class asks.
            registry.add("spring.jpa.properties.hibernate.generate_statistics") { "true" }
        }
    }

    private fun statistics(): Statistics =
        entityManager.entityManagerFactory.unwrap(SessionFactory::class.java).statistics

    @BeforeEach
    fun setUp() {
        senderId = seedUser()
        conversationId = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = conversationId, type = ConversationType.GROUP, name = "Bahçe Katı", createdBy = senderId)
        )
    }

    @Test
    fun `should not read a row before inserting it when a message and its delivery rows are written`() {
        val recipients = List(4) { seedUser() }
        recipients.forEach {
            conversationRepository.saveMember(ConversationMember(conversationId = conversationId, userId = it))
        }
        val stats = statistics()
        stats.clear()

        transactions.inTransaction {
            val message = messageRepository.save(newMessage())
            messageRepository.saveDeliveryStatuses(
                recipients.map {
                    MessageDeliveryStatus(messageId = message.id, userId = it, status = DeliveryStatus.SENT)
                }
            )
            message
        }

        // 1 message + 4 delivery rows.
        assertEquals(5, stats.entityInsertCount, "expected exactly one insert per new row")

        // The comparison is "no MORE statements than rows", not "equal to": batching may make it
        // fewer, which is the other half of the fix. What cannot happen any more is more — before
        // this, each insert was preceded by a merge's SELECT for a row that cannot exist yet, so
        // five rows cost ten statements.
        assertTrue(
            stats.prepareStatementCount <= stats.entityInsertCount,
            "5 new rows cost ${stats.prepareStatementCount} statements; anything above the insert " +
                "count means a read crept back in front of a write (#492)"
        )
    }

    @Test
    fun `should batch the delivery rows into fewer statements than rows when a group message is written`() {
        val recipients = List(30) { seedUser() }
        recipients.forEach {
            conversationRepository.saveMember(ConversationMember(conversationId = conversationId, userId = it))
        }
        val stats = statistics()
        stats.clear()

        transactions.inTransaction {
            val message = messageRepository.save(newMessage())
            messageRepository.saveDeliveryStatuses(
                recipients.map {
                    MessageDeliveryStatus(messageId = message.id, userId = it, status = DeliveryStatus.SENT)
                }
            )
            message
        }

        assertEquals(31, stats.entityInsertCount)
        // batch_size is 25, so 30 delivery rows are two batches, plus the message: far fewer
        // prepared statements than rows. The point is that it batches at all — it could not while
        // every insert was preceded by a select.
        assertTrue(
            stats.prepareStatementCount < 31,
            "expected the inserts to batch, but ${stats.prepareStatementCount} statements were prepared for 31 rows"
        )
    }

    private fun newMessage() = Message(
        id = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = senderId,
        contentType = ContentType.TEXT,
        content = "merhaba",
        clientTimestamp = Instant.now()
    )

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 is unallocated in Turkey, so a number that leaks reaches nobody.
        val phone = "+90500" + (1_000_000 + (0..8_999_999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }
}

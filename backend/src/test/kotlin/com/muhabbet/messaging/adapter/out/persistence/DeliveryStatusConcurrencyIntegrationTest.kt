package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.model.ReceiptAdvance
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * The delivery-status store against a real PostgreSQL, including under real concurrency (#870).
 *
 * The defect this exists for: the old adapter read the row, checked `READ` in Kotlin, and saved the
 * whole entity. Two writers — the WebSocket READ and the REST DELIVERED (#596) — could both read
 * SENT, and whichever saved last won, so DELIVERED could be stored over READ. The replacement is a
 * conditional `UPDATE`; these tests drive the exact interleaving that broke the old code, with one
 * transaction parked holding the row lock while the other waits on it, and check the stored row.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class DeliveryStatusConcurrencyIntegrationTest {

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var transactions: TransactionRunner

    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var conversationId: UUID
    private lateinit var alice: UUID
    private lateinit var bob: UUID
    private lateinit var reader: UUID

    private val base = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS)
    private var clock = 0L

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

        private const val LOCK_WAIT_TIMEOUT_MS = 10_000L
        private const val STRESS_ROUNDS = 40
    }

    @BeforeEach
    fun setUp() {
        alice = seedUser()
        bob = seedUser()
        reader = seedUser()
        conversationId = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = conversationId, type = ConversationType.GROUP, name = "Bahçe Katı", createdBy = alice)
        )
        listOf(alice, bob, reader).forEach {
            conversationRepository.saveMember(ConversationMember(conversationId = conversationId, userId = it))
        }
    }

    // ─── Single-row transitions ──────────────────────────────

    @Test
    fun `should advance SENT to DELIVERED to READ and report each move`() {
        val message = seedMessage(from = alice, to = listOf(reader))

        val delivered = advance(message, reader, DeliveryStatus.DELIVERED)
        val read = advance(message, reader, DeliveryStatus.READ)

        assertEquals(ReceiptAdvance(message, conversationId, alice), delivered)
        assertEquals(ReceiptAdvance(message, conversationId, alice), read)
        assertEquals(DeliveryStatus.READ, stored(message, reader))
    }

    @Test
    fun `should neither store nor report DELIVERED over READ`() {
        val message = seedMessage(from = alice, to = listOf(reader))
        advance(message, reader, DeliveryStatus.READ)

        assertNull(advance(message, reader, DeliveryStatus.DELIVERED))
        assertEquals(DeliveryStatus.READ, stored(message, reader))
    }

    @Test
    fun `should report nothing for a repeated receipt`() {
        val message = seedMessage(from = alice, to = listOf(reader))
        assertNotNull(advance(message, reader, DeliveryStatus.DELIVERED))

        assertNull(advance(message, reader, DeliveryStatus.DELIVERED))
        assertNull(advance(message, reader, DeliveryStatus.SENT))
    }

    @Test
    fun `should report nothing for a caller with no row`() {
        // Not a recipient (or blocked, so never given a row): a no-op that cannot be used to probe
        // whether an arbitrary message id exists.
        val message = seedMessage(from = alice, to = listOf(reader))

        assertNull(advance(message, bob, DeliveryStatus.DELIVERED))
        assertNull(advance(UUID.randomUUID(), reader, DeliveryStatus.DELIVERED))
    }

    // ─── Concurrency ─────────────────────────────────────────

    @Test
    fun `should keep READ when a DELIVERED waits on the row lock READ holds`() {
        // The interleaving that broke the old read-then-save: the DELIVERED writer starts while READ
        // is written but not committed. Under the old code it read SENT, then saved DELIVERED after
        // READ committed. Here it blocks on the row, re-checks the committed READ, and matches nothing.
        val message = seedMessage(from = alice, to = listOf(reader))
        val (first, second) = racePinned(message, firstTo = DeliveryStatus.READ, secondTo = DeliveryStatus.DELIVERED)

        assertNotNull(first)
        assertNull(second, "DELIVERED reported an advance over a committed READ")
        assertEquals(DeliveryStatus.READ, stored(message, reader))
    }

    @Test
    fun `should still reach READ when READ waits on the row lock DELIVERED holds`() {
        val message = seedMessage(from = alice, to = listOf(reader))
        val (first, second) = racePinned(message, firstTo = DeliveryStatus.DELIVERED, secondTo = DeliveryStatus.READ)

        assertNotNull(first)
        assertNotNull(second, "READ must advance past a committed DELIVERED")
        assertEquals(DeliveryStatus.READ, stored(message, reader))
    }

    @Test
    fun `should never leave DELIVERED stored when READ and DELIVERED race freely`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(STRESS_ROUNDS) {
                val message = seedMessage(from = alice, to = listOf(reader))
                val barrier = CyclicBarrier(2)
                val read = pool.submit<ReceiptAdvance?> { barrier.await(); advance(message, reader, DeliveryStatus.READ) }
                val delivered = pool.submit<ReceiptAdvance?> { barrier.await(); advance(message, reader, DeliveryStatus.DELIVERED) }
                assertNotNull(read.get(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS), "READ always advances from SENT or DELIVERED")
                delivered.get(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                assertEquals(DeliveryStatus.READ, stored(message, reader))
            }
        } finally {
            pool.shutdownNow()
        }
    }

    // ─── Mark read up to ─────────────────────────────────────

    @Test
    fun `should mark read every earlier incoming message and report each with its sender`() {
        val fromAlice = List(3) { seedMessage(from = alice, to = listOf(bob, reader)) }
        val alreadyRead = seedMessage(from = alice, to = listOf(reader))
        advance(alreadyRead, reader, DeliveryStatus.READ)
        val own = seedMessage(from = reader, to = listOf(alice, bob))
        val deleted = seedMessage(from = bob, to = listOf(reader))
        transactions.inTransaction { messageRepository.softDelete(deleted) }
        val fromBob = List(2) { seedMessage(from = bob, to = listOf(reader)) }
        val upTo = fromBob.last()
        val after = seedMessage(from = alice, to = listOf(reader))

        val advanced = transactions.inTransaction { messageRepository.markReadUpTo(conversationId, reader, upTo) }

        assertEquals(
            (fromAlice.map { ReceiptAdvance(it, conversationId, alice) } + fromBob.map { ReceiptAdvance(it, conversationId, bob) }).toSet(),
            advanced.toSet()
        )
        (fromAlice + fromBob + alreadyRead).forEach { assertEquals(DeliveryStatus.READ, stored(it, reader)) }
        assertEquals(DeliveryStatus.SENT, stored(after, reader), "a message after the acked one stays unread")
        assertEquals(DeliveryStatus.SENT, stored(deleted, reader))
        // Another member's rows are not the reader's to change.
        fromAlice.forEach { assertEquals(DeliveryStatus.SENT, stored(it, bob)) }
        assertEquals(DeliveryStatus.SENT, stored(own, alice))
    }

    @Test
    fun `should mark nothing for an upTo message outside the conversation`() {
        seedMessage(from = alice, to = listOf(reader))
        val elsewhere = UUID.randomUUID()
        conversationRepository.save(Conversation(id = elsewhere, type = ConversationType.GROUP, name = "Başka", createdBy = alice))
        val foreign = seedMessage(from = alice, to = listOf(reader), conversation = elsewhere)

        val advanced = transactions.inTransaction { messageRepository.markReadUpTo(conversationId, reader, foreign) }

        assertTrue(advanced.isEmpty())
    }

    // ─── Aggregate input ─────────────────────────────────────

    @Test
    fun `should report the distinct states present per message`() {
        val mixed = seedMessage(from = alice, to = listOf(bob, reader))
        advance(mixed, reader, DeliveryStatus.READ)
        val uniform = seedMessage(from = alice, to = listOf(bob, reader))

        val sets = messageRepository.findDeliveryStatusSets(listOf(mixed, uniform, UUID.randomUUID()))

        assertEquals(setOf(DeliveryStatus.SENT, DeliveryStatus.READ), sets[mixed])
        assertEquals(setOf(DeliveryStatus.SENT), sets[uniform])
        assertEquals(2, sets.size, "a message with no rows is absent")
    }

    // ─── Helpers ─────────────────────────────────────────────

    private fun advance(message: UUID, user: UUID, to: DeliveryStatus): ReceiptAdvance? =
        transactions.inTransaction { listOfNotNull(messageRepository.advanceDeliveryStatus(message, user, to)) }.firstOrNull()

    /**
     * Runs [firstTo] in a transaction that stays open, holding the row lock, until [secondTo] has
     * been seen waiting for that lock in `pg_stat_activity` — then lets the first commit. That makes
     * the interleaving deterministic instead of hoping a race lands.
     */
    private fun racePinned(message: UUID, firstTo: DeliveryStatus, secondTo: DeliveryStatus): Pair<ReceiptAdvance?, ReceiptAdvance?> {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val firstWritten = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val first: Future<ReceiptAdvance?> = pool.submit<ReceiptAdvance?> {
                transactions.inTransaction {
                    val result = listOfNotNull(messageRepository.advanceDeliveryStatus(message, reader, firstTo))
                    firstWritten.countDown()
                    releaseFirst.await(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    result
                }.firstOrNull()
            }
            assertTrue(firstWritten.await(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            val second: Future<ReceiptAdvance?> = pool.submit<ReceiptAdvance?> { advance(message, reader, secondTo) }
            assertTrue(awaitLockWaiter(), "the second writer never blocked on the row lock")
            releaseFirst.countDown()
            return first.get(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) to second.get(LOCK_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun awaitLockWaiter(): Boolean {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT count(*) FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock' AND query ILIKE '%update message_delivery_status%'
                    """.trimIndent()
                ).executeQuery().use { rows ->
                    rows.next()
                    if (rows.getInt(1) > 0) return true
                }
            }
            Thread.sleep(20)
        }
        return false
    }

    private fun stored(message: UUID, user: UUID): DeliveryStatus? =
        messageRepository.getDeliveryStatuses(listOf(message)).firstOrNull { it.userId == user }?.status

    private fun seedMessage(from: UUID, to: List<UUID>, conversation: UUID = conversationId): UUID {
        val id = UUID.randomUUID()
        clock += 1
        transactions.inTransaction {
            messageRepository.save(
                Message(
                    id = id,
                    conversationId = conversation,
                    senderId = from,
                    content = "m-$clock",
                    serverTimestamp = base.plusSeconds(clock),
                    clientTimestamp = base.plusSeconds(clock)
                )
            )
            messageRepository.saveDeliveryStatuses(to.map { MessageDeliveryStatus(id, it, DeliveryStatus.SENT) })
            id
        }
        return id
    }

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 is unallocated in Turkey, so a number that leaks reaches nobody.
        val phone = "+90500" + (1_000_000 + (0..8_999_999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }
}

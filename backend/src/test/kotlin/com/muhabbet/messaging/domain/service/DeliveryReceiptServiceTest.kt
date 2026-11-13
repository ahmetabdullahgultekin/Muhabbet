package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.DeliveryTransition
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.model.Receipt
import com.muhabbet.messaging.domain.model.ReceiptAdvance
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.FailingTransactionRunner
import com.muhabbet.shared.InlineTransactionRunner
import com.muhabbet.shared.TestMediaAttachmentPolicy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The receipt use case (#870, #871) against an in-memory delivery store.
 *
 * The store's three receipt methods are answered from a map with exactly the contract the SQL
 * adapter has — a row moves only when `stored.advance(to)` is Applied, and only moved rows come
 * back — so these tests exercise the service's publish decisions, not a mock's script. The SQL
 * itself, and its behaviour under real concurrency, is `DeliveryStatusConcurrencyIntegrationTest`.
 */
class DeliveryReceiptServiceTest {

    private data class StoredMessage(val conversationId: UUID, val senderId: UUID, val order: Int)

    private val messageRepository: MessageRepository = mockk()
    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val broadcaster: MessageBroadcaster = mockk(relaxed = true)
    private val readReceiptPolicy: ReadReceiptPolicyPort = mockk()

    private val messages = mutableMapOf<UUID, StoredMessage>()
    private val rows = mutableMapOf<Pair<UUID, UUID>, DeliveryStatus>()

    private val conversationId = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()
    private val dave = UUID.randomUUID()

    private fun service(transactions: TransactionRunner = InlineTransactionRunner()) = DeliveryReceiptService(
        messageRepository = messageRepository,
        conversationRepository = conversationRepository,
        messageBroadcaster = broadcaster,
        readReceiptPolicy = readReceiptPolicy,
        transactions = transactions
    )

    private fun advanceRow(messageId: UUID, userId: UUID, to: DeliveryStatus): ReceiptAdvance? {
        val key = messageId to userId
        val stored = rows[key] ?: return null
        val transition = stored.advance(to) as? DeliveryTransition.Applied ?: return null
        rows[key] = transition.to
        val message = messages.getValue(messageId)
        return ReceiptAdvance(messageId, message.conversationId, message.senderId)
    }

    @BeforeEach
    fun setUp() {
        every { readReceiptPolicy.findReadReceiptsDisabled(any()) } returns emptySet()
        every { messageRepository.advanceDeliveryStatus(any(), any(), any()) } answers {
            advanceRow(firstArg(), secondArg(), thirdArg())
        }
        every { messageRepository.markReadUpTo(any(), any(), any()) } answers {
            val conv = firstArg<UUID>()
            val reader = secondArg<UUID>()
            val upTo = messages[thirdArg<UUID>()]?.takeIf { it.conversationId == conv }
            if (upTo == null) {
                emptyList()
            } else {
                messages.filter { (_, m) -> m.conversationId == conv && m.senderId != reader && m.order <= upTo.order }
                    .keys.sortedBy { messages.getValue(it).order }
                    .mapNotNull { advanceRow(it, reader, DeliveryStatus.READ) }
            }
        }
        every { messageRepository.findDeliveryStatusSets(any()) } answers {
            val ids = firstArg<Collection<UUID>>().toSet()
            rows.entries.filter { it.key.first in ids }
                .groupBy({ it.key.first }, { it.value })
                .mapValues { (_, statuses) -> statuses.toSet() }
        }
        every { messageRepository.getDeliveryStatuses(any()) } answers {
            val ids = firstArg<List<UUID>>().toSet()
            rows.entries.filter { it.key.first in ids }.map { MessageDeliveryStatus(it.key.first, it.key.second, it.value) }
        }
    }

    private fun send(from: UUID, to: List<UUID>, initial: DeliveryStatus = DeliveryStatus.SENT): UUID {
        val id = UUID.randomUUID()
        messages[id] = StoredMessage(conversationId, from, messages.size)
        to.forEach { rows[id to it] = initial }
        return id
    }

    private fun publishedTo(sender: UUID, messageId: UUID, reader: UUID, status: DeliveryStatus) =
        verify(exactly = 1) { broadcaster.broadcastStatusUpdate(messageId, conversationId, reader, sender, status) }

    // ─── Monotone, idempotent (#870) ────────────────────────────

    @Test
    fun `should publish DELIVERED to the sender when a 1-to-1 message reaches the recipient`() {
        val message = send(from = alice, to = listOf(bob))

        service().record(Receipt.Delivered(message, bob))

        publishedTo(alice, message, bob, DeliveryStatus.DELIVERED)
        assertEquals(DeliveryStatus.DELIVERED, rows[message to bob])
    }

    @Test
    fun `should publish nothing for a late DELIVERED after READ`() {
        // The slow REST ack (#596) landing after the recipient read the chat. It used to be
        // published regardless and turned the sender's blue ticks grey, live.
        val message = send(from = alice, to = listOf(bob), initial = DeliveryStatus.READ)

        service().record(Receipt.Delivered(message, bob))

        verify(exactly = 0) { broadcaster.broadcastStatusUpdate(any(), any(), any(), any(), any()) }
        assertEquals(DeliveryStatus.READ, rows[message to bob])
    }

    @Test
    fun `should publish nothing for a repeated receipt`() {
        val message = send(from = alice, to = listOf(bob))

        service().record(Receipt.Read(conversationId, message, bob))
        service().record(Receipt.Read(conversationId, message, bob))
        service().record(Receipt.Delivered(message, bob))

        verify(exactly = 1) { broadcaster.broadcastStatusUpdate(any(), any(), any(), any(), any()) }
        publishedTo(alice, message, bob, DeliveryStatus.READ)
    }

    @Test
    fun `should publish nothing for a message the caller has no row for`() {
        val message = send(from = alice, to = listOf(bob))

        service().record(Receipt.Delivered(message, carol))

        verify(exactly = 0) { broadcaster.broadcastStatusUpdate(any(), any(), any(), any(), any()) }
    }

    // ─── Read fan-out covers every message (#870) ───────────────

    @Test
    fun `should publish every message a READ marked read, each to its own sender`() {
        // Group of alice, bob, carol (reader dave). Five unread messages from two senders. The old
        // pipeline bulk-updated all five and published one — the acked id — so alice heard about
        // her last message and nothing else, and bob heard nothing at all.
        val fromAlice = (1..3).map { send(from = alice, to = listOf(dave)) }
        val fromBob = (1..2).map { send(from = bob, to = listOf(dave)) }
        val last = fromBob.last()

        service().record(Receipt.Read(conversationId, last, dave))

        fromAlice.forEach { publishedTo(alice, it, dave, DeliveryStatus.READ) }
        fromBob.forEach { publishedTo(bob, it, dave, DeliveryStatus.READ) }
        verify(exactly = 5) { broadcaster.broadcastStatusUpdate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should leave messages after the acked one unread`() {
        val earlier = send(from = alice, to = listOf(dave))
        val acked = send(from = alice, to = listOf(dave))
        val later = send(from = alice, to = listOf(dave))

        service().record(Receipt.Read(conversationId, acked, dave))

        assertEquals(DeliveryStatus.READ, rows[earlier to dave])
        assertEquals(DeliveryStatus.READ, rows[acked to dave])
        assertEquals(DeliveryStatus.SENT, rows[later to dave])
        verify(exactly = 0) { broadcaster.broadcastStatusUpdate(later, any(), any(), any(), any()) }
    }

    @Test
    fun `should advance the reader's last-read marker on READ even when nothing was unread`() {
        val message = send(from = alice, to = listOf(bob), initial = DeliveryStatus.READ)

        service().record(Receipt.Read(conversationId, message, bob))

        verify(exactly = 1) { conversationRepository.updateLastReadAt(conversationId, bob, any()) }
    }

    // ─── Group aggregate (#871) ─────────────────────────────────

    @Test
    fun `should keep a group message DELIVERED until every member has read it`() {
        // 3 recipients. The first READ used to turn the sender's tick blue for the whole group.
        val message = send(from = alice, to = listOf(bob, carol, dave), initial = DeliveryStatus.DELIVERED)

        service().record(Receipt.Read(conversationId, message, bob))
        publishedTo(alice, message, bob, DeliveryStatus.DELIVERED)

        service().record(Receipt.Read(conversationId, message, carol))
        publishedTo(alice, message, carol, DeliveryStatus.DELIVERED)

        service().record(Receipt.Read(conversationId, message, dave))
        publishedTo(alice, message, dave, DeliveryStatus.READ)

        verify(exactly = 0) { broadcaster.broadcastStatusUpdate(message, any(), bob, any(), DeliveryStatus.READ) }
    }

    @Test
    fun `should keep a group message SENT until every member has received it`() {
        val message = send(from = alice, to = listOf(bob, carol))

        service().record(Receipt.Delivered(message, bob))
        publishedTo(alice, message, bob, DeliveryStatus.SENT)

        service().record(Receipt.Delivered(message, carol))
        publishedTo(alice, message, carol, DeliveryStatus.DELIVERED)
    }

    @Test
    fun `should publish the same tick the history path resolves`() {
        // Live and reloaded ticks are computed by one function (senderView). Drive a group through
        // a sequence of receipts and compare each published tick with the history resolution of
        // the rows as they stood right after it.
        val message = send(from = alice, to = listOf(bob, carol))
        val history = MessageService(
            conversationRepository, messageRepository, broadcaster, mockk(relaxed = true),
            readReceiptPolicy, mockk(relaxed = true), TestMediaAttachmentPolicy(),
            InlineTransactionRunner()
        )
        val sentMessage = Message(
            id = message, conversationId = conversationId, senderId = alice, content = "x",
            clientTimestamp = Instant.now()
        )
        val published = mutableListOf<DeliveryStatus>()
        every { broadcaster.broadcastStatusUpdate(message, any(), any(), any(), any()) } answers {
            published += arg<DeliveryStatus>(4)
        }

        listOf(
            Receipt.Delivered(message, bob),
            Receipt.Read(conversationId, message, bob),
            Receipt.Delivered(message, carol),
            Receipt.Read(conversationId, message, carol)
        ).forEach { receipt ->
            service().record(receipt)
            assertEquals(history.resolveDeliveryStatuses(listOf(sentMessage), alice)[message], published.last())
        }
        assertEquals(
            listOf(DeliveryStatus.SENT, DeliveryStatus.SENT, DeliveryStatus.DELIVERED, DeliveryStatus.READ),
            published
        )
    }

    @Test
    fun `should publish READ as DELIVERED when a reader has read receipts off`() {
        val message = send(from = alice, to = listOf(bob), initial = DeliveryStatus.DELIVERED)
        every { readReceiptPolicy.findReadReceiptsDisabled(any()) } returns setOf(bob)

        service().record(Receipt.Read(conversationId, message, bob))

        // Stored READ (clears bob's own badge); published DELIVERED (alice is not told).
        assertEquals(DeliveryStatus.READ, rows[message to bob])
        publishedTo(alice, message, bob, DeliveryStatus.DELIVERED)
    }

    // ─── Transaction boundary (#669, carried over from MessageService) ──

    private class RecordingRunner(private val broadcasts: () -> Int) : TransactionRunner {
        var broadcastsAtCommit = -1
        override fun <T : Any> inTransaction(block: () -> T): T {
            val result = block()
            broadcastsAtCommit = broadcasts()
            return result
        }
    }

    @Test
    fun `should not publish until the receipt has committed`() {
        var broadcasts = 0
        every { broadcaster.broadcastStatusUpdate(any(), any(), any(), any(), any()) } answers { broadcasts++ }
        val runner = RecordingRunner { broadcasts }
        val message = send(from = alice, to = listOf(bob))

        service(runner).record(Receipt.Read(conversationId, message, bob))

        assertEquals(0, runner.broadcastsAtCommit, "the status fan-out ran inside the transaction")
        assertEquals(1, broadcasts, "the status fan-out did not run after the commit either")
    }

    @Test
    fun `should publish nothing when the receipt fails to commit`() {
        val message = send(from = alice, to = listOf(bob))

        assertThrows(IllegalStateException::class.java) {
            service(FailingTransactionRunner(IllegalStateException("commit failed")))
                .record(Receipt.Delivered(message, bob))
        }

        verify(exactly = 0) { broadcaster.broadcastStatusUpdate(any(), any(), any(), any(), any()) }
    }
}

package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.InlineTransactionRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * #560 — one undeliverable scheduled message must not stop every other scheduled message.
 *
 * The batch shared a single transaction, so a failure on the third message rolled back the
 * `markAsDelivered` of the first two as well: they were still due, the next run a minute later
 * selected the same batch in the same `scheduled_at ASC` order, and hit the same message. Nothing
 * recovered from it, and the only symptom was one log line a minute.
 *
 * Two things had to change together. This class asserts the first: a `try/catch` around the loop
 * body would not have been enough — Spring Data's write methods join the surrounding transaction
 * and mark it rollback-only, so the catch would swallow an exception that had already doomed the
 * commit and the run would die at commit time reporting success. Real per-message boundaries are
 * what these tests check for.
 *
 * The second is that the method-level `@Transactional` had to go, because `TransactionRunner` uses
 * REQUIRED propagation: with an outer transaction still open, per-message boundaries would simply
 * join it and the isolation would be imaginary. **That half is not asserted here and cannot be** —
 * every test below injects an [InlineTransactionRunner], so all five pass whether the annotation is
 * present or not, which was verified by putting it back. It is guarded instead by
 * [ScheduledDeliveryTransactionBoundaryTest], and the two classes are only meaningful together.
 */
class ScheduledDeliveryIsolationTest {

    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val messageBroadcaster: MessageBroadcaster = mockk(relaxed = true)
    private val blockPolicy: BlockPolicyPort = mockk(relaxed = true)

    private val sender = UUID.randomUUID()
    private val recipient = UUID.randomUUID()

    private fun service(transactions: TransactionRunner = InlineTransactionRunner()) = MessageService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster,
        userDirectory = mockk(relaxed = true),
        readReceiptPolicy = mockk(relaxed = true),
        blockPolicy = blockPolicy,
        transactions = transactions
    )

    private fun scheduled(conversationId: UUID) = Message(
        id = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = sender,
        contentType = ContentType.TEXT,
        content = "zamanlanmış",
        clientTimestamp = Instant.now(),
        isScheduled = true,
        scheduledAt = Instant.now().minusSeconds(60)
    )

    @BeforeEach
    fun setUp() {
        every { blockPolicy.hasBlocked(any(), any()) } returns false
        every { conversationRepository.findById(any()) } answers {
            Conversation(id = firstArg(), type = ConversationType.GROUP)
        }
        every { conversationRepository.findMembersByConversationId(any()) } answers {
            listOf(
                ConversationMember(conversationId = firstArg(), userId = sender),
                ConversationMember(conversationId = firstArg(), userId = recipient)
            )
        }
    }

    @Test
    fun `should deliver the rest of the batch when one message cannot be delivered`() {
        val poison = scheduled(UUID.randomUUID())
        val healthy = scheduled(UUID.randomUUID())
        every { messageRepository.findScheduledMessagesReadyToSend(any(), any()) } returns listOf(poison, healthy)
        every { messageRepository.markAsDelivered(poison.id) } throws IllegalStateException("row is wedged")

        val delivered = service().deliverScheduledMessages()

        assertEquals(1, delivered, "the healthy message should still have been delivered")
        verify(exactly = 1) { messageBroadcaster.broadcastMessage(healthy, any()) }
        verify(exactly = 0) { messageBroadcaster.broadcastMessage(poison, any()) }
    }

    @Test
    fun `should keep going when the failure is in the fan-out rather than the write`() {
        // A broadcast now happens outside the message's transaction, so its failure cannot roll the
        // delivery back — but it must not abort the run either.
        val first = scheduled(UUID.randomUUID())
        val second = scheduled(UUID.randomUUID())
        every { messageRepository.findScheduledMessagesReadyToSend(any(), any()) } returns listOf(first, second)
        every { messageBroadcaster.broadcastMessage(first, any()) } throws RuntimeException("redis down")

        val delivered = service().deliverScheduledMessages()

        assertEquals(1, delivered)
        verify(exactly = 1) { messageBroadcaster.broadcastMessage(second, any()) }
    }

    @Test
    fun `should give each message its own transaction when a batch is delivered`() {
        val messages = List(3) { scheduled(UUID.randomUUID()) }
        every { messageRepository.findScheduledMessagesReadyToSend(any(), any()) } returns messages
        var transactions = 0
        val counting = object : TransactionRunner {
            override fun <T : Any> inTransaction(block: () -> T): T {
                transactions++
                return block()
            }
        }

        service(counting).deliverScheduledMessages()

        // One per message, not one for the batch. If this ever reads 1 again, a failure anywhere
        // takes the whole run with it.
        assertEquals(3, transactions)
    }

    @Test
    fun `should broadcast only after the message's own transaction has committed`() {
        val message = scheduled(UUID.randomUUID())
        every { messageRepository.findScheduledMessagesReadyToSend(any(), any()) } returns listOf(message)
        var broadcastsAtCommit = -1
        var broadcasts = 0
        every { messageBroadcaster.broadcastMessage(any(), any()) } answers { broadcasts++; Unit }
        val recording = object : TransactionRunner {
            override fun <T : Any> inTransaction(block: () -> T): T {
                val result = block()
                broadcastsAtCommit = broadcasts
                return result
            }
        }

        service(recording).deliverScheduledMessages()

        assertEquals(0, broadcastsAtCommit, "the fan-out ran inside the message's transaction")
        assertEquals(1, broadcasts)
    }

    @Test
    fun `should ask for a bounded batch rather than every due message`() {
        every { messageRepository.findScheduledMessagesReadyToSend(any(), any()) } returns emptyList()

        service().deliverScheduledMessages()

        verify { messageRepository.findScheduledMessagesReadyToSend(any(), match { it in 1..1000 }) }
    }
}

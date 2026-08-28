package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.FailingTransactionRunner
import com.muhabbet.shared.protocol.WsMessage
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
 * #669 — the three message mutations #491 did not reach.
 *
 * `sendMessage` got its boundary moved in #491 and `SendMessageTransactionBoundaryTest` pins it.
 * `updateStatus`, `deleteMessage` and `editMessage` kept the old shape for months: `@Transactional`
 * on the method, the WebSocket fan-out inside it. Both defects came with it — a Hikari connection
 * held across blocking socket writes to every member, and a client handed an event before the
 * transaction that produced it had committed.
 *
 * `updateStatus` is the one that mattered most by volume: it runs on every delivery and every read
 * receipt, roughly once per message delivered.
 *
 * A unit test cannot watch a connection go back into the pool, so it watches the thing that kept it
 * out: whether the broadcaster is reached from inside the transactional block. Two assertions per
 * site, the same pair `SendMessageTransactionBoundaryTest` makes — nothing goes out before the
 * commit, and nothing goes out at all if the commit fails.
 *
 * Restoring `@Transactional` to any of these three fails this file, which is the point of it: with
 * REQUIRED propagation the annotation would swallow the inner boundary and the fix would look
 * present in the source while doing nothing.
 */
class MessageMutationTransactionBoundaryTest {

    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val messageBroadcaster: MessageBroadcaster = mockk(relaxed = true)
    private val userDirectory: UserDirectoryPort = mockk(relaxed = true)
    private val readReceiptPolicy: ReadReceiptPolicyPort = mockk(relaxed = true)
    private val blockPolicy: BlockPolicyPort = mockk(relaxed = true)

    private val sender = UUID.randomUUID()
    private val recipient = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()
    private val messageId = UUID.randomUUID()

    private fun message() = Message(
        id = messageId,
        conversationId = conversationId,
        senderId = sender,
        content = "merhaba",
        serverTimestamp = Instant.now(),
        clientTimestamp = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        every { blockPolicy.hasBlocked(any(), any()) } returns false
        every { readReceiptPolicy.findReadReceiptsDisabled(any()) } returns emptySet()
        every { messageRepository.findById(messageId) } returns message()
        every { conversationRepository.findById(conversationId) } returns
            Conversation(id = conversationId, type = ConversationType.GROUP)
        every { conversationRepository.findMembersByConversationId(conversationId) } returns listOf(
            ConversationMember(conversationId = conversationId, userId = sender),
            ConversationMember(conversationId = conversationId, userId = recipient)
        )
    }

    private fun serviceWith(transactions: TransactionRunner) = MessageService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster,
        userDirectory = userDirectory,
        readReceiptPolicy = readReceiptPolicy,
        blockPolicy = blockPolicy,
        transactions = transactions
    )

    /**
     * Records how many broadcasts had been made at the moment the block returned — before the
     * commit. Anything but zero means the fan-out is back inside the transaction. It starts at -1
     * so that a method which never opens a transaction at all fails too, rather than passing by
     * never being asked.
     */
    private class RecordingRunner(private val broadcasterCalls: () -> Int) : TransactionRunner {
        var callsAtCommit: Int = -1
        override fun <T : Any> inTransaction(block: () -> T): T {
            val result = block()
            callsAtCommit = broadcasterCalls()
            return result
        }
    }

    // ─── updateStatus ────────────────────────────────────────

    @Test
    fun `should not broadcast until the transaction has committed when a delivery status is updated`() {
        var broadcasts = 0
        every { messageBroadcaster.broadcastStatusUpdate(any(), any(), any(), any(), any()) } answers
            { broadcasts++; Unit }
        val runner = RecordingRunner { broadcasts }

        serviceWith(runner).updateStatus(messageId, recipient, DeliveryStatus.READ)

        assertEquals(0, runner.callsAtCommit, "the status fan-out ran inside the transaction")
        assertEquals(1, broadcasts, "the status fan-out did not run after the commit either")
    }

    @Test
    fun `should not broadcast a delivery status when the transaction fails to commit`() {
        assertThrows(IllegalStateException::class.java) {
            serviceWith(FailingTransactionRunner(IllegalStateException("commit failed")))
                .updateStatus(messageId, recipient, DeliveryStatus.READ)
        }

        verify(exactly = 0) { messageBroadcaster.broadcastStatusUpdate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should publish READ as DELIVERED when the reader has read receipts off`() {
        every { readReceiptPolicy.findReadReceiptsDisabled(listOf(recipient)) } returns setOf(recipient)
        val runner = RecordingRunner { 0 }

        serviceWith(runner).updateStatus(messageId, recipient, DeliveryStatus.READ)

        // The downgrade decision is made inside the transaction and carried out to the fan-out.
        // Moving the boundary must not lose it: the stored row stays READ so the reader's own
        // unread badge clears, and only what is published is softened.
        verify {
            messageBroadcaster.broadcastStatusUpdate(
                messageId,
                conversationId,
                recipient,
                sender,
                DeliveryStatus.DELIVERED
            )
        }
        verify { messageRepository.updateDeliveryStatus(messageId, recipient, DeliveryStatus.READ) }
    }

    // ─── deleteMessage ───────────────────────────────────────

    @Test
    fun `should not broadcast until the transaction has committed when a message is deleted`() {
        var broadcasts = 0
        every { messageBroadcaster.broadcastToUsers(any(), any()) } answers { broadcasts++; Unit }
        val runner = RecordingRunner { broadcasts }

        serviceWith(runner).deleteMessage(messageId, sender)

        assertEquals(0, runner.callsAtCommit, "the delete fan-out ran inside the transaction")
        assertEquals(1, broadcasts, "the delete fan-out did not run after the commit either")
    }

    @Test
    fun `should not broadcast a deletion when the transaction fails to commit`() {
        assertThrows(IllegalStateException::class.java) {
            serviceWith(FailingTransactionRunner(IllegalStateException("commit failed")))
                .deleteMessage(messageId, sender)
        }

        verify(exactly = 0) { messageBroadcaster.broadcastToUsers(any(), any<WsMessage.MessageDeleted>()) }
    }

    // ─── editMessage ─────────────────────────────────────────

    @Test
    fun `should not broadcast until the transaction has committed when a message is edited`() {
        var broadcasts = 0
        every { messageBroadcaster.broadcastToUsers(any(), any()) } answers { broadcasts++; Unit }
        val runner = RecordingRunner { broadcasts }

        serviceWith(runner).editMessage(messageId, sender, "düzeltildi")

        assertEquals(0, runner.callsAtCommit, "the edit fan-out ran inside the transaction")
        assertEquals(1, broadcasts, "the edit fan-out did not run after the commit either")
    }

    @Test
    fun `should not broadcast an edit when the transaction fails to commit`() {
        assertThrows(IllegalStateException::class.java) {
            serviceWith(FailingTransactionRunner(IllegalStateException("commit failed")))
                .editMessage(messageId, sender, "düzeltildi")
        }

        verify(exactly = 0) { messageBroadcaster.broadcastToUsers(any(), any<WsMessage.MessageEdited>()) }
    }

    @Test
    fun `should return the edited content to the caller when a message is edited`() {
        val edited = serviceWith(RecordingRunner { 0 }).editMessage(messageId, sender, "düzeltildi")

        assertEquals("düzeltildi", edited.content)
        verify { messageRepository.updateContent(messageId, "düzeltildi", any()) }
    }
}

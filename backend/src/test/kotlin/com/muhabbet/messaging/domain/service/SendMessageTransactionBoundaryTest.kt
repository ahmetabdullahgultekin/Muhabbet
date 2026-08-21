package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.FailingTransactionRunner
import com.muhabbet.shared.InlineTransactionRunner
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
 * #491 — the WebSocket fan-out must happen **after** the database transaction, not inside it.
 *
 * Two properties are at stake and this file exists for both. The one that shows up as an outage is
 * resource: while `broadcastMessage` ran inside the transaction, the Hikari connection stayed
 * checked out for the length of the slowest recipient's socket — twenty seconds, at Tomcat's
 * blocking send timeout — so a pool of twenty capped the whole instance at twenty concurrent sends.
 * The one that shows up as a bug report is ordering: recipients were handed a message before the
 * transaction that created it committed, so a rollback left them holding one the database did not
 * have.
 *
 * A unit test cannot observe a Hikari connection, so it observes the thing that caused it: whether
 * the broadcaster is reached from inside the transactional block. That is the invariant a future
 * edit would break, and it is checkable here without a database.
 */
class SendMessageTransactionBoundaryTest {

    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val messageBroadcaster: MessageBroadcaster = mockk(relaxed = true)
    private val userDirectory: UserDirectoryPort = mockk(relaxed = true)
    private val readReceiptPolicy: ReadReceiptPolicyPort = mockk(relaxed = true)
    private val blockPolicy: BlockPolicyPort = mockk(relaxed = true)

    private val sender = UUID.randomUUID()
    private val recipient = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { blockPolicy.hasBlocked(any(), any()) } returns false
        every { conversationRepository.findMember(conversationId, sender) } returns
            ConversationMember(conversationId = conversationId, userId = sender)
        every { conversationRepository.findById(conversationId) } returns
            Conversation(id = conversationId, type = ConversationType.GROUP)
        every { conversationRepository.findMembersByConversationId(conversationId) } returns listOf(
            ConversationMember(conversationId = conversationId, userId = sender),
            ConversationMember(conversationId = conversationId, userId = recipient)
        )
        every { messageRepository.existsById(any()) } returns false
        every { messageRepository.save(any()) } answers { firstArg() }
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

    private fun command() = SendMessageCommand(
        messageId = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = sender,
        content = "merhaba",
        clientTimestamp = Instant.now()
    )

    /**
     * A runner that records what the broadcaster had been asked to do at the moment the block
     * returned — i.e. before the commit. Anything but zero means the fan-out is back inside the
     * transaction.
     */
    private class RecordingRunner(private val broadcasterCalls: () -> Int) : TransactionRunner {
        var callsAtCommit: Int = -1
        override fun <T : Any> inTransaction(block: () -> T): T {
            val result = block()
            callsAtCommit = broadcasterCalls()
            return result
        }
    }

    @Test
    fun `should not broadcast until the transaction has committed when a message is sent`() {
        var broadcasts = 0
        every { messageBroadcaster.broadcastMessage(any(), any()) } answers { broadcasts++; Unit }
        val runner = RecordingRunner { broadcasts }

        serviceWith(runner).sendMessage(command())

        assertEquals(0, runner.callsAtCommit, "the fan-out ran inside the transaction")
        assertEquals(1, broadcasts, "the fan-out did not run after the commit either")
    }

    @Test
    fun `should not broadcast at all when the transaction fails to commit`() {
        val boom = IllegalStateException("commit failed")

        assertThrows(IllegalStateException::class.java) {
            serviceWith(FailingTransactionRunner(boom)).sendMessage(command())
        }

        // The old shape broadcast from inside the transaction, so recipients kept a message the
        // rollback removed. Nothing may go out for work that did not commit.
        verify(exactly = 0) { messageBroadcaster.broadcastMessage(any(), any()) }
    }

    @Test
    fun `should return the saved message to the caller when a message is sent`() {
        val command = command()

        val sent: Message = serviceWith(InlineTransactionRunner()).sendMessage(command)

        assertEquals(command.messageId, sent.id)
        assertEquals(command.content, sent.content)
    }
}

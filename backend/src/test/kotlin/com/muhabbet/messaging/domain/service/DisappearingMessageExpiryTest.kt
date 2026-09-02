package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.TestData
import com.muhabbet.shared.protocol.WsMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The half of disappearing messages that was never built (#513).
 *
 * The sweep itself was correct and measured: rows expired on time and vanished from every read
 * path. It simply told nobody. A chat left open therefore went on rendering a message the server
 * had already deleted — indefinitely, until the user navigated away and back. That is worse than
 * cosmetic in a conversation both people have open, because the entire promise of the feature is a
 * bound on how long a message exists, and a bound only the server observes is not that promise.
 *
 * Nothing could have caught it before: the job held a `SpringDataMessageRepository` and flipped a
 * boolean, so there was no seam to assert a broadcast on and nothing to inject a fake into.
 */
class DisappearingMessageExpiryTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var broadcaster: MessageBroadcaster
    private lateinit var service: DisappearingMessageService

    /** What happened, in the order it happened — see the ordering test at the bottom. */
    private val events = mutableListOf<String>()

    private val conversationId = TestData.CONVERSATION_ID
    private val alice = TestData.USER_ID_1
    private val bob = TestData.USER_ID_2

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        broadcaster = mockk(relaxed = true)
        events.clear()
        // Runs the block, and records that the transaction ended. The real runner returns only
        // once the transaction has committed, which is the property the ordering test depends on.
        val transactions = object : TransactionRunner {
            override fun <T : Any> inTransaction(block: () -> T): T =
                block().also { events += "committed" }
        }
        every { messageRepository.softDeleteExpired(any(), any()) } answers { events += "deleted"; 1 }
        every { broadcaster.broadcastToUsers(any(), any()) } answers { events += "broadcast" }
        service = DisappearingMessageService(
            conversationRepository, messageRepository, broadcaster, transactions
        )
    }

    private fun expired(id: UUID, conversation: UUID = conversationId): Message =
        TestData.textMessage(id = id, conversationId = conversation, senderId = alice)
            .copy(expiresAt = Instant.now().minusSeconds(30))

    @Test
    fun `should tell every member when a message expires`() {
        val messageId = TestData.MESSAGE_ID
        every { messageRepository.findExpiredMessages(any(), any()) } returns listOf(expired(messageId))
        every { conversationRepository.findMembersByConversationIds(listOf(conversationId)) } returns
            mapOf(conversationId to listOf(TestData.member(userId = alice), TestData.member(userId = bob)))

        service.expireDueMessages()

        val recipients = slot<List<UUID>>()
        val frame = slot<WsMessage>()
        verify(exactly = 1) { broadcaster.broadcastToUsers(capture(recipients), capture(frame)) }

        assertEquals(listOf(alice, bob), recipients.captured)
        val expiry = frame.captured as WsMessage.MessageExpired
        assertEquals(messageId.toString(), expiry.messageId)
        assertEquals(conversationId.toString(), expiry.conversationId)
    }

    /**
     * The sender is told as well as the recipient. A disappearing message disappears from the
     * conversation, not from one side of it — leaving the author's copy on screen would make the
     * feature look like it had failed for exactly the person who chose to use it.
     */
    @Test
    fun `should include the sender among the members told`() {
        every { messageRepository.findExpiredMessages(any(), any()) } returns listOf(expired(TestData.MESSAGE_ID))
        every { conversationRepository.findMembersByConversationIds(any()) } returns
            mapOf(conversationId to listOf(TestData.member(userId = alice), TestData.member(userId = bob)))

        service.expireDueMessages()

        val recipients = slot<List<UUID>>()
        verify { broadcaster.broadcastToUsers(capture(recipients), any()) }
        assertTrue(alice in recipients.captured, "the author was not told their own message expired")
    }

    @Test
    fun `should delete the expired rows in one statement rather than one per message`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every { messageRepository.findExpiredMessages(any(), any()) } returns
            listOf(expired(first), expired(second))
        every { conversationRepository.findMembersByConversationIds(any()) } returns
            mapOf(conversationId to listOf(TestData.member(userId = alice)))

        service.expireDueMessages()

        val ids = slot<List<UUID>>()
        verify(exactly = 1) { messageRepository.softDeleteExpired(capture(ids), any()) }
        assertEquals(listOf(first, second), ids.captured)
    }

    /**
     * One member lookup for the whole sweep, not one per message. A timer belongs to a
     * conversation, so a batch of two hundred expired messages is usually a handful of
     * conversations — asking per message is how a background job becomes the most expensive query
     * on the box.
     */
    @Test
    fun `should read the member list once per conversation when many messages expire together`() {
        val other = UUID.randomUUID()
        every { messageRepository.findExpiredMessages(any(), any()) } returns listOf(
            expired(UUID.randomUUID()),
            expired(UUID.randomUUID()),
            expired(UUID.randomUUID(), conversation = other)
        )
        every { conversationRepository.findMembersByConversationIds(any()) } returns
            mapOf(
                conversationId to listOf(TestData.member(userId = alice)),
                other to listOf(TestData.member(conversationId = other, userId = bob))
            )

        val count = service.expireDueMessages()

        assertEquals(3, count)
        val ids = slot<List<UUID>>()
        verify(exactly = 1) { conversationRepository.findMembersByConversationIds(capture(ids)) }
        assertEquals(listOf(conversationId, other), ids.captured)
        // One frame per message, because a client removes messages by id.
        verify(exactly = 3) { broadcaster.broadcastToUsers(any(), any()) }
    }

    /**
     * At one run a minute for the life of the product, the overwhelming majority of sweeps find
     * nothing. None of them should touch the database beyond the one query that asked, or emit a
     * log line — 1,440 lines a day saying nothing is how the runs that matter get buried.
     */
    @Test
    fun `should do nothing when no message is due`() {
        every { messageRepository.findExpiredMessages(any(), any()) } returns emptyList()

        assertEquals(0, service.expireDueMessages())

        verify(exactly = 0) { messageRepository.softDeleteExpired(any(), any()) }
        verify(exactly = 0) { conversationRepository.findMembersByConversationIds(any()) }
        verify(exactly = 0) { broadcaster.broadcastToUsers(any(), any()) }
    }

    /**
     * A conversation whose members cannot be resolved still gets its row deleted — the delete is
     * the guarantee, the broadcast is the courtesy. Broadcasting to an empty list would be a
     * pointless round trip through Redis.
     */
    @Test
    fun `should still delete the message when its members cannot be resolved`() {
        every { messageRepository.findExpiredMessages(any(), any()) } returns listOf(expired(TestData.MESSAGE_ID))
        every { conversationRepository.findMembersByConversationIds(any()) } returns emptyMap()

        assertEquals(1, service.expireDueMessages())

        verify(exactly = 1) { messageRepository.softDeleteExpired(any(), any()) }
        verify(exactly = 0) { broadcaster.broadcastToUsers(any(), any()) }
    }

    /**
     * The delete must be committed before anyone is told, and the telling must not happen inside
     * the transaction.
     *
     * Ordering, because a member who reacts to the frame by re-fetching the conversation would
     * otherwise be able to read the row before the delete commits and be handed the message
     * straight back — the message would blink out and return. Resources, because this publishes
     * once per expired message and a transaction spanning the fan-out holds a pool connection for
     * all of it (#491).
     */
    @Test
    fun `should commit the delete before telling anyone`() {
        every { messageRepository.findExpiredMessages(any(), any()) } returns listOf(expired(TestData.MESSAGE_ID))
        every { conversationRepository.findMembersByConversationIds(any()) } returns
            mapOf(conversationId to listOf(TestData.member(userId = alice)))

        service.expireDueMessages()

        assertEquals(listOf("deleted", "committed", "broadcast"), events)
    }

    /**
     * The sweep is `fixedDelay`, so a long run delays the next one rather than overlapping it, and
     * it now broadcasts as well as deletes. An unbounded query is how a backlog turns one run into
     * a very long one.
     */
    @Test
    fun `should ask for a bounded batch of due messages`() {
        every { messageRepository.findExpiredMessages(any(), any()) } returns emptyList()

        service.expireDueMessages()

        verify { messageRepository.findExpiredMessages(any(), DisappearingMessageService.EXPIRY_BATCH_LIMIT) }
    }
}

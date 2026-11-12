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
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.InMemoryConversationSequence
import com.muhabbet.shared.InlineTransactionRunner
import com.muhabbet.shared.TestMediaAttachmentPolicy
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * #750 — every message gets a place in its conversation, and a retried send is answered with the
 * original ack rather than an error (ADR 0013 §3, ADR 0014).
 *
 * The allocation's real guarantee — gapless under concurrency, held by the conversation row lock —
 * needs a database and is asserted in `ConversationSequenceIntegrationTest`. This class pins the
 * decisions the service makes around it: what gets numbered and when, and which resends replay.
 */
class MessageSequenceAndReplayTest {

    private val sequence = InMemoryConversationSequence()
    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val messageBroadcaster: MessageBroadcaster = mockk(relaxed = true)
    private val userDirectory: UserDirectoryPort = mockk(relaxed = true)
    private val readReceiptPolicy: ReadReceiptPolicyPort = mockk(relaxed = true)
    private val blockPolicy: BlockPolicyPort = mockk(relaxed = true)

    private val sender = UUID.randomUUID()
    private val recipient = UUID.randomUUID()
    private val device = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()

    private val service = MessageService(
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        messageBroadcaster = messageBroadcaster,
        userDirectory = userDirectory,
        readReceiptPolicy = readReceiptPolicy,
        blockPolicy = blockPolicy,
        mediaAttachmentPolicy = TestMediaAttachmentPolicy(),
        transactions = InlineTransactionRunner(),
        sequence = sequence
    )

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
        every { messageRepository.markAsDelivered(any(), any(), any()) } returns true
    }

    private fun command(
        messageId: UUID = UUID.randomUUID(),
        senderId: UUID = sender,
        senderDeviceId: UUID? = device,
        scheduledAt: Instant? = null
    ) = SendMessageCommand(
        messageId = messageId,
        conversationId = conversationId,
        senderId = senderId,
        content = "merhaba",
        clientTimestamp = Instant.now(),
        scheduledAt = scheduledAt,
        senderDeviceId = senderDeviceId
    )

    private fun committed(id: UUID, senderId: UUID = sender, deviceId: UUID? = device, seq: Long = 7L) = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        content = "merhaba",
        clientTimestamp = Instant.now(),
        serverTimestamp = Instant.parse("2026-11-12T10:00:00Z"),
        seq = seq,
        changeSeq = seq,
        senderDeviceId = deviceId
    )

    // ─── Allocation ──────────────────────────────────────

    @Test
    fun `should number each immediate send from the conversation sequence when messages are sent`() {
        val first = service.sendMessage(command())
        val second = service.sendMessage(command())

        assertEquals(1L, first.message.seq)
        assertEquals(1L, first.message.changeSeq)
        assertEquals(2L, second.message.seq)
        assertEquals(device, first.message.senderDeviceId)
        assertFalse(first.replay)
    }

    @Test
    fun `should leave a scheduled send unnumbered when it is not yet due`() {
        val result = service.sendMessage(command(scheduledAt = Instant.now().plusSeconds(3600)))

        assertNull(result.message.seq)
        assertEquals(0L, sequence.lastFor(conversationId), "a scheduled send must not spend a seq")
        verify(exactly = 0) { messageBroadcaster.broadcastMessage(any(), any()) }
    }

    @Test
    fun `should number a scheduled message and move its timestamp when it is released`() {
        val writtenAt = Instant.parse("2026-11-12T09:00:00Z")
        val due = committed(UUID.randomUUID()).copy(
            seq = null, changeSeq = null, isScheduled = true, serverTimestamp = writtenAt,
            scheduledAt = Instant.now().minusSeconds(5)
        )
        every { messageRepository.findScheduledMessagesReadyToSend(any(), any()) } returns listOf(due)
        val broadcast = slot<Message>()
        every { messageBroadcaster.broadcastMessage(capture(broadcast), any()) } returns Unit

        service.deliverScheduledMessages()

        verify { messageRepository.markAsDelivered(due.id, 1L, any()) }
        assertEquals(1L, broadcast.captured.seq)
        assertFalse(broadcast.captured.isScheduled)
        assertTrue(broadcast.captured.serverTimestamp.isAfter(writtenAt))
    }

    @Test
    fun `should send nothing when another run released the scheduled message first`() {
        val due = committed(UUID.randomUUID()).copy(seq = null, isScheduled = true)
        every { messageRepository.findScheduledMessagesReadyToSend(any(), any()) } returns listOf(due)
        every { messageRepository.markAsDelivered(due.id, any(), any()) } returns false

        assertEquals(0, service.deliverScheduledMessages())

        verify(exactly = 0) { messageRepository.saveDeliveryStatuses(any()) }
        verify(exactly = 0) { messageBroadcaster.broadcastMessage(any(), any()) }
    }

    @Test
    fun `should stamp a new change seq when a message is edited or deleted`() {
        val messageId = UUID.randomUUID()
        sequence.next(conversationId) // the message itself took seq 1
        every { messageRepository.findById(messageId) } returns
            committed(messageId, seq = 1L).copy(serverTimestamp = Instant.now())

        service.editMessage(messageId, sender, "düzeltildi")
        service.deleteMessage(messageId, sender)

        verify { messageRepository.updateContent(messageId, "düzeltildi", any(), 2L) }
        verify { messageRepository.softDelete(messageId, 3L) }
    }

    @Test
    fun `should tell only the sender and spend no seq when an unreleased scheduled message is edited`() {
        val messageId = UUID.randomUUID()
        every { messageRepository.findById(messageId) } returns committed(messageId).copy(
            seq = null, changeSeq = null, isScheduled = true, serverTimestamp = Instant.now(),
            scheduledAt = Instant.now().plusSeconds(3600)
        )
        every { messageRepository.updateScheduledContent(messageId, "gizli metin", any()) } returns true
        val recipients = slot<List<UUID>>()
        every { messageBroadcaster.broadcastToUsers(capture(recipients), any()) } returns Unit

        service.editMessage(messageId, sender, "gizli metin")

        assertEquals(listOf(sender), recipients.captured, "the edited text must not reach a recipient early")
        assertEquals(0L, sequence.lastFor(conversationId), "an unreleased scheduled row has no place to stamp")
        verify(exactly = 0) { messageRepository.updateContent(any(), any(), any(), any()) }
    }

    @Test
    fun `should tell only the sender and spend no seq when an unreleased scheduled message is deleted`() {
        val messageId = UUID.randomUUID()
        every { messageRepository.findById(messageId) } returns committed(messageId).copy(
            seq = null, changeSeq = null, isScheduled = true, scheduledAt = Instant.now().plusSeconds(3600)
        )
        every { messageRepository.softDeleteScheduled(messageId) } returns true
        val recipients = slot<List<UUID>>()
        every { messageBroadcaster.broadcastToUsers(capture(recipients), any()) } returns Unit

        service.deleteMessage(messageId, sender)

        assertEquals(listOf(sender), recipients.captured)
        assertEquals(0L, sequence.lastFor(conversationId))
        verify(exactly = 0) { messageRepository.softDelete(any(), any()) }
    }

    @Test
    fun `should treat the delete as live when the scheduler released the message first`() {
        val messageId = UUID.randomUUID()
        every { messageRepository.findById(messageId) } returns committed(messageId).copy(
            seq = null, changeSeq = null, isScheduled = true
        )
        every { messageRepository.softDeleteScheduled(messageId) } returns false
        val recipients = slot<List<UUID>>()
        every { messageBroadcaster.broadcastToUsers(capture(recipients), any()) } returns Unit

        service.deleteMessage(messageId, sender)

        assertEquals(setOf(sender, recipient), recipients.captured.toSet())
        verify { messageRepository.softDelete(messageId, 1L) }
    }

    // ─── Replay ──────────────────────────────────────────

    @Test
    fun `should replay the original ack and write nothing when the same device resends a message id`() {
        val messageId = UUID.randomUUID()
        every { messageRepository.existsById(messageId) } returns true
        every { messageRepository.findById(messageId) } returns committed(messageId)

        val result = service.sendMessage(command(messageId = messageId))

        assertTrue(result.replay)
        assertEquals(7L, result.message.seq)
        assertEquals(Instant.parse("2026-11-12T10:00:00Z"), result.message.serverTimestamp)
        verify(exactly = 0) { messageRepository.save(any()) }
        verify(exactly = 0) { messageBroadcaster.broadcastMessage(any(), any()) }
        assertEquals(0L, sequence.lastFor(conversationId), "a replay must not allocate")
    }

    @Test
    fun `should replay a legacy row when it has no device recorded and the same sender resends`() {
        val messageId = UUID.randomUUID()
        every { messageRepository.existsById(messageId) } returns true
        every { messageRepository.findById(messageId) } returns committed(messageId, deviceId = null)

        assertTrue(service.sendMessage(command(messageId = messageId)).replay)
    }

    @Test
    fun `should refuse with MSG_DUPLICATE when the id belongs to another user's message`() {
        val messageId = UUID.randomUUID()
        every { messageRepository.existsById(messageId) } returns true
        every { messageRepository.findById(messageId) } returns committed(messageId, senderId = UUID.randomUUID())

        val error = assertThrows(BusinessException::class.java) { service.sendMessage(command(messageId = messageId)) }

        assertEquals(ErrorCode.MSG_DUPLICATE, error.errorCode)
        verify(exactly = 0) { messageRepository.save(any()) }
    }

    @Test
    fun `should refuse with MSG_DUPLICATE when another device of the same user reuses the id`() {
        val messageId = UUID.randomUUID()
        every { messageRepository.existsById(messageId) } returns true
        every { messageRepository.findById(messageId) } returns committed(messageId, deviceId = UUID.randomUUID())

        val error = assertThrows(BusinessException::class.java) { service.sendMessage(command(messageId = messageId)) }

        assertEquals(ErrorCode.MSG_DUPLICATE, error.errorCode)
    }

    @Test
    fun `should replay the winner when a concurrent copy of the same send takes the insert first`() {
        // Both copies looked before either committed, so the lookup said "new" to both; the loser
        // learns otherwise only when its insert hits the primary key.
        val messageId = UUID.randomUUID()
        every { messageRepository.existsById(messageId) } returnsMany listOf(false, true)
        every { messageRepository.save(any()) } throws BusinessException(ErrorCode.MSG_DUPLICATE)
        every { messageRepository.findById(messageId) } returns committed(messageId)

        val result = service.sendMessage(command(messageId = messageId))

        assertTrue(result.replay)
        assertEquals(7L, result.message.seq)
        verify(exactly = 0) { messageBroadcaster.broadcastMessage(any(), any()) }
    }
}

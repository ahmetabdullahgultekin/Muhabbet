package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.*
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

class DeliveryStatusTest {

    private val conversationRepository = mockk<ConversationRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val messageBroadcaster = mockk<MessageBroadcaster>(relaxed = true)

    private lateinit var service: MessageService

    @BeforeEach
    fun setUp() {
        service = MessageService(conversationRepository, messageRepository, messageBroadcaster)
    }

    @Test
    fun `should resolve SENT when no delivery statuses exist`() {
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val message = createMessage(messageId, senderId)

        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns emptyList()

        val result = service.resolveDeliveryStatuses(listOf(message), senderId)
        assertEquals(DeliveryStatus.SENT, result[messageId])
    }

    @Test
    fun `should resolve DELIVERED when any recipient has DELIVERED status`() {
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val recipientA = UUID.randomUUID()
        val recipientB = UUID.randomUUID()
        val message = createMessage(messageId, senderId)

        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns listOf(
            MessageDeliveryStatus(messageId, recipientA, DeliveryStatus.DELIVERED),
            MessageDeliveryStatus(messageId, recipientB, DeliveryStatus.SENT)
        )

        val result = service.resolveDeliveryStatuses(listOf(message), senderId)
        assertEquals(DeliveryStatus.DELIVERED, result[messageId])
    }

    @Test
    fun `should resolve READ when all recipients have READ status`() {
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val recipientA = UUID.randomUUID()
        val recipientB = UUID.randomUUID()
        val message = createMessage(messageId, senderId)

        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns listOf(
            MessageDeliveryStatus(messageId, recipientA, DeliveryStatus.READ),
            MessageDeliveryStatus(messageId, recipientB, DeliveryStatus.READ)
        )

        val result = service.resolveDeliveryStatuses(listOf(message), senderId)
        assertEquals(DeliveryStatus.READ, result[messageId])
    }

    @Test
    fun `should resolve own status when requesting user is recipient`() {
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val recipientId = UUID.randomUUID()
        val message = createMessage(messageId, senderId)

        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns listOf(
            MessageDeliveryStatus(messageId, recipientId, DeliveryStatus.DELIVERED)
        )

        val result = service.resolveDeliveryStatuses(listOf(message), recipientId)
        assertEquals(DeliveryStatus.DELIVERED, result[messageId])
    }

    @Test
    fun `should handle batch resolution efficiently`() {
        val senderId = UUID.randomUUID()
        val msg1Id = UUID.randomUUID()
        val msg2Id = UUID.randomUUID()
        val msg3Id = UUID.randomUUID()
        val recipientId = UUID.randomUUID()

        val messages = listOf(
            createMessage(msg1Id, senderId),
            createMessage(msg2Id, senderId),
            createMessage(msg3Id, senderId)
        )

        every { messageRepository.getDeliveryStatuses(listOf(msg1Id, msg2Id, msg3Id)) } returns listOf(
            MessageDeliveryStatus(msg1Id, recipientId, DeliveryStatus.READ),
            MessageDeliveryStatus(msg2Id, recipientId, DeliveryStatus.DELIVERED),
            MessageDeliveryStatus(msg3Id, recipientId, DeliveryStatus.SENT)
        )

        val result = service.resolveDeliveryStatuses(messages, senderId)
        assertEquals(DeliveryStatus.READ, result[msg1Id])
        assertEquals(DeliveryStatus.DELIVERED, result[msg2Id])
        assertEquals(DeliveryStatus.SENT, result[msg3Id])
    }

    @Test
    fun `should return empty map for empty message list`() {
        val result = service.resolveDeliveryStatuses(emptyList(), UUID.randomUUID())
        assertTrue(result.isEmpty())
    }

    // ─── Roadmap Tier 1.2 explicit scenarios ────────────────────────────────
    // These lock the receipt aggregation contract (single-tick / double-tick UX)
    // so it cannot silently regress when the message path is re-wired under E2E.

    @Test
    fun `1to1 sender sees SENT then DELIVERED then READ as the single recipient progresses`() {
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val recipientId = UUID.randomUUID()
        val message = createMessage(messageId, senderId)

        // Stage 1: SENT — recipient row exists but only SENT.
        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns listOf(
            MessageDeliveryStatus(messageId, recipientId, DeliveryStatus.SENT)
        )
        assertEquals(
            DeliveryStatus.SENT,
            service.resolveDeliveryStatuses(listOf(message), senderId)[messageId],
            "Single-tick: only SENT recorded"
        )

        // Stage 2: DELIVERED — recipient acked delivery.
        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns listOf(
            MessageDeliveryStatus(messageId, recipientId, DeliveryStatus.DELIVERED)
        )
        assertEquals(
            DeliveryStatus.DELIVERED,
            service.resolveDeliveryStatuses(listOf(message), senderId)[messageId],
            "Double-tick grey: DELIVERED"
        )

        // Stage 3: READ — recipient opened the conversation.
        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns listOf(
            MessageDeliveryStatus(messageId, recipientId, DeliveryStatus.READ)
        )
        assertEquals(
            DeliveryStatus.READ,
            service.resolveDeliveryStatuses(listOf(message), senderId)[messageId],
            "Double-tick blue: READ"
        )
    }

    @Test
    fun `group with partial reads stays DELIVERED for the sender`() {
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val readMember = UUID.randomUUID()
        val deliveredMember = UUID.randomUUID()
        val sentOnlyMember = UUID.randomUUID()
        val message = createMessage(messageId, senderId)

        // One member READ, one only DELIVERED, one only SENT — NOT all read.
        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns listOf(
            MessageDeliveryStatus(messageId, readMember, DeliveryStatus.READ),
            MessageDeliveryStatus(messageId, deliveredMember, DeliveryStatus.DELIVERED),
            MessageDeliveryStatus(messageId, sentOnlyMember, DeliveryStatus.SENT)
        )

        assertEquals(
            DeliveryStatus.DELIVERED,
            service.resolveDeliveryStatuses(listOf(message), senderId)[messageId],
            "Group is not all-read, so the aggregate must stay at DELIVERED (blue ticks only once everyone reads)"
        )
    }

    @Test
    fun `group with all members read resolves to READ for the sender`() {
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val memberA = UUID.randomUUID()
        val memberB = UUID.randomUUID()
        val memberC = UUID.randomUUID()
        val message = createMessage(messageId, senderId)

        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns listOf(
            MessageDeliveryStatus(messageId, memberA, DeliveryStatus.READ),
            MessageDeliveryStatus(messageId, memberB, DeliveryStatus.READ),
            MessageDeliveryStatus(messageId, memberC, DeliveryStatus.READ)
        )

        assertEquals(
            DeliveryStatus.READ,
            service.resolveDeliveryStatuses(listOf(message), senderId)[messageId],
            "Every group member read it → READ"
        )
    }

    @Test
    fun `a recipient sees only their own status row, never the aggregate`() {
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val recipientRead = UUID.randomUUID()
        val recipientDelivered = UUID.randomUUID()
        val message = createMessage(messageId, senderId)

        val statuses = listOf(
            MessageDeliveryStatus(messageId, recipientRead, DeliveryStatus.READ),
            MessageDeliveryStatus(messageId, recipientDelivered, DeliveryStatus.DELIVERED)
        )
        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns statuses

        // The recipient who only DELIVERED must see DELIVERED — NOT the other member's READ,
        // and NOT the sender-aggregate.
        assertEquals(
            DeliveryStatus.DELIVERED,
            service.resolveDeliveryStatuses(listOf(message), recipientDelivered)[messageId],
            "Recipient must see ONLY their own row (DELIVERED), not another member's READ"
        )
        // The recipient who READ sees their own READ row.
        assertEquals(
            DeliveryStatus.READ,
            service.resolveDeliveryStatuses(listOf(message), recipientRead)[messageId],
            "Recipient must see their own READ row"
        )
    }

    @Test
    fun `a recipient with no row of their own falls back to SENT, not another member's status`() {
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val otherMember = UUID.randomUUID()
        val viewer = UUID.randomUUID() // has no row in the result set
        val message = createMessage(messageId, senderId)

        every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns listOf(
            MessageDeliveryStatus(messageId, otherMember, DeliveryStatus.READ)
        )

        assertEquals(
            DeliveryStatus.SENT,
            service.resolveDeliveryStatuses(listOf(message), viewer)[messageId],
            "Viewer without their own row must default to SENT, never leak another member's READ"
        )
    }

    @Test
    fun `updateStatus persists the row and broadcasts to the sender`() {
        val messageId = UUID.randomUUID()
        val senderId = UUID.randomUUID()
        val recipientId = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        val message = createMessage(messageId, senderId).copy(conversationId = conversationId)

        every { messageRepository.updateDeliveryStatus(messageId, recipientId, DeliveryStatus.READ) } just Runs
        every { messageRepository.findById(messageId) } returns message

        service.updateStatus(messageId, recipientId, DeliveryStatus.READ)

        verify(exactly = 1) { messageRepository.updateDeliveryStatus(messageId, recipientId, DeliveryStatus.READ) }
        verify(exactly = 1) {
            messageBroadcaster.broadcastStatusUpdate(messageId, conversationId, recipientId, senderId, DeliveryStatus.READ)
        }
    }

    @Test
    fun `markConversationRead bulk-marks the conversation and advances last-read for the reader`() {
        val conversationId = UUID.randomUUID()
        val readerId = UUID.randomUUID()

        every { messageRepository.markConversationRead(conversationId, readerId) } just Runs
        every { conversationRepository.updateLastReadAt(conversationId, readerId, any()) } just Runs

        service.markConversationRead(conversationId, readerId)

        verify(exactly = 1) { messageRepository.markConversationRead(conversationId, readerId) }
        verify(exactly = 1) { conversationRepository.updateLastReadAt(conversationId, readerId, any()) }
    }

    private fun createMessage(id: UUID, senderId: UUID) = Message(
        id = id,
        conversationId = UUID.randomUUID(),
        senderId = senderId,
        contentType = ContentType.TEXT,
        content = "test",
        serverTimestamp = Instant.now(),
        clientTimestamp = Instant.now()
    )
}

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

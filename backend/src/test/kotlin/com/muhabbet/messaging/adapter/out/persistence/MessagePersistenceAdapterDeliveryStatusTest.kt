package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageDeliveryStatusJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageDeliveryStatusRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageRepository
import com.muhabbet.messaging.domain.model.DeliveryStatus
import io.mockk.every
import jakarta.persistence.EntityManager
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * #596 added a second writer for a delivery row — a REST ack a background FCM service can send with
 * no socket open — alongside the WebSocket ack `ChatWebSocketHandler` already sent. The two can now
 * race: a `DELIVERED` REST call queued behind the push, and a `READ` WebSocket ack from the chat the
 * recipient opened in the meantime. Whichever lands second must not undo the other when it is the
 * REST call that is late — READ is what actually happened, and the sender must not see the tick
 * fall backwards.
 */
class MessagePersistenceAdapterDeliveryStatusTest {

    private val messageRepo: SpringDataMessageRepository = mockk()
    private val deliveryStatusRepo: SpringDataMessageDeliveryStatusRepository = mockk()
    private val entityManager: EntityManager = mockk(relaxed = true)
    private val adapter = MessagePersistenceAdapter(messageRepo, deliveryStatusRepo, entityManager)

    private val messageId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    private fun existingRow(status: DeliveryStatus) = MessageDeliveryStatusJpaEntity(
        messageId = messageId,
        userId = userId,
        status = status
    )

    @Test
    fun `should not downgrade an already-READ row to DELIVERED`() {
        every { deliveryStatusRepo.findByMessageIdAndUserId(messageId, userId) } returns existingRow(DeliveryStatus.READ)

        adapter.updateDeliveryStatus(messageId, userId, DeliveryStatus.DELIVERED)

        verify(exactly = 0) { deliveryStatusRepo.save(any()) }
    }

    @Test
    fun `should still allow a READ row to be written as READ again`() {
        // Idempotency, not just monotonicity: the guard must not turn a harmless re-send into a
        // silent no-op that looks identical to the downgrade it exists to block.
        val entity = existingRow(DeliveryStatus.READ)
        every { deliveryStatusRepo.findByMessageIdAndUserId(messageId, userId) } returns entity
        val saved = slot<MessageDeliveryStatusJpaEntity>()
        every { deliveryStatusRepo.save(capture(saved)) } returns entity

        adapter.updateDeliveryStatus(messageId, userId, DeliveryStatus.READ)

        assertEquals(DeliveryStatus.READ, saved.captured.status)
    }

    @Test
    fun `should apply DELIVERED over SENT as before`() {
        val entity = existingRow(DeliveryStatus.SENT)
        every { deliveryStatusRepo.findByMessageIdAndUserId(messageId, userId) } returns entity
        val saved = slot<MessageDeliveryStatusJpaEntity>()
        every { deliveryStatusRepo.save(capture(saved)) } returns entity

        adapter.updateDeliveryStatus(messageId, userId, DeliveryStatus.DELIVERED)

        assertEquals(DeliveryStatus.DELIVERED, saved.captured.status)
    }

    @Test
    fun `should do nothing for a recipient with no delivery row`() {
        // A non-recipient, or a message dropped for a block, never had a row created — same no-op
        // shape the WebSocket ack handler already relies on, so this cannot be used to probe whether
        // an arbitrary message id exists.
        every { deliveryStatusRepo.findByMessageIdAndUserId(messageId, userId) } returns null

        adapter.updateDeliveryStatus(messageId, userId, DeliveryStatus.DELIVERED)

        verify(exactly = 0) { deliveryStatusRepo.save(any()) }
    }
}

package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageDeliveryStatusRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageRepository
import com.muhabbet.messaging.domain.model.DeliveryStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * #596 added a second writer for a delivery row — a REST ack a background FCM service can send with
 * no socket open — alongside the WebSocket ack `ChatWebSocketHandler` already sent. The two can
 * race: a `DELIVERED` REST call queued behind the push, and a `READ` WebSocket ack from the chat the
 * recipient opened in the meantime. Whichever lands second must not undo the other.
 *
 * Since #299 the guard is one conditional UPDATE that only moves a row forward, and its count is
 * the answer to "did anything change" — which decides whether the sender is told at all.
 */
class MessagePersistenceAdapterDeliveryStatusTest {

    private val messageRepo: SpringDataMessageRepository = mockk()
    private val deliveryStatusRepo: SpringDataMessageDeliveryStatusRepository = mockk()
    private val entityManager: EntityManager = mockk(relaxed = true)
    private val adapter = MessagePersistenceAdapter(messageRepo, deliveryStatusRepo, entityManager)

    private val messageId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `should only advance a row from statuses below DELIVERED when DELIVERED is acked`() {
        every { deliveryStatusRepo.advanceStatus(messageId, userId, DeliveryStatus.DELIVERED, any(), any()) } returns 1

        assertTrue(adapter.updateDeliveryStatus(messageId, userId, DeliveryStatus.DELIVERED))

        // READ is not in the list, so a READ row can never be dragged back to DELIVERED.
        verify {
            deliveryStatusRepo.advanceStatus(messageId, userId, DeliveryStatus.DELIVERED, listOf(DeliveryStatus.SENT), any())
        }
    }

    @Test
    fun `should advance from SENT or DELIVERED when READ is acked`() {
        every { deliveryStatusRepo.advanceStatus(messageId, userId, DeliveryStatus.READ, any(), any()) } returns 1

        assertTrue(adapter.updateDeliveryStatus(messageId, userId, DeliveryStatus.READ))

        verify {
            deliveryStatusRepo.advanceStatus(
                messageId,
                userId,
                DeliveryStatus.READ,
                listOf(DeliveryStatus.SENT, DeliveryStatus.DELIVERED),
                any()
            )
        }
    }

    @Test
    fun `should report no change when the row is already at or past the acked status`() {
        // An already-READ row acked DELIVERED, a repeated READ, or no row at all (a non-recipient,
        // a message dropped for a block): zero rows updated, and the caller broadcasts nothing.
        every { deliveryStatusRepo.advanceStatus(any(), any(), any(), any(), any()) } returns 0

        assertFalse(adapter.updateDeliveryStatus(messageId, userId, DeliveryStatus.DELIVERED))
    }

    @Test
    fun `should not touch the database when SENT is acked`() {
        // Nothing ranks below SENT, so there is no row it could move.
        assertFalse(adapter.updateDeliveryStatus(messageId, userId, DeliveryStatus.SENT))

        verify(exactly = 0) { deliveryStatusRepo.advanceStatus(any(), any(), any(), any(), any()) }
    }
}

package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageDeliveryStatusJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.MessageJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageDeliveryStatusRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageRepository
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * #492 — writing a new row must not first read it.
 *
 * `SimpleJpaRepository.save` chooses between `persist` and `merge` by asking whether the id is
 * null. Every id in this application is assigned — the client mints the message id, and a delivery
 * row is keyed by (messageId, userId) — so the answer was always "not new" and every save was a
 * `merge`. A merge reads the row it is about to copy onto, so each insert on the send path was
 * preceded by a SELECT that could only ever come back empty: one for the message and one for every
 * recipient. Those interleaved reads are also what stopped `hibernate.jdbc.batch_size` from ever
 * batching anything.
 *
 * Counting the statements themselves needs a real database and therefore Testcontainers, which does
 * not run on a machine without Docker. What this asserts instead is the decision that produced
 * them: the adapter calls `persist`, and the repository's `save` is not on this path at all. That
 * is the thing a future edit would undo.
 */
class MessageInsertIsAnInsertTest {

    private val messageRepo: SpringDataMessageRepository = mockk(relaxed = true)
    private val deliveryStatusRepo: SpringDataMessageDeliveryStatusRepository = mockk(relaxed = true)
    private val entityManager: EntityManager = mockk(relaxed = true)
    private val adapter = MessagePersistenceAdapter(messageRepo, deliveryStatusRepo, entityManager)

    private val messageId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()
    private val senderId = UUID.randomUUID()

    private fun message() = Message(
        id = messageId,
        conversationId = conversationId,
        senderId = senderId,
        contentType = ContentType.TEXT,
        content = "merhaba",
        clientTimestamp = Instant.now()
    )

    @Test
    fun `should persist a message rather than merge it when it is saved`() {
        adapter.save(message())

        verify(exactly = 1) { entityManager.persist(any<MessageJpaEntity>()) }
        verify(exactly = 0) { messageRepo.save(any()) }
    }

    @Test
    fun `should return the saved message when a message is saved`() {
        val saved = adapter.save(message())

        assertEquals(messageId, saved.id)
        assertEquals("merhaba", saved.content)
    }

    @Test
    fun `should persist every delivery row without a merge when a group message is saved`() {
        val recipients = List(3) { UUID.randomUUID() }

        adapter.saveDeliveryStatuses(
            recipients.map { MessageDeliveryStatus(messageId = messageId, userId = it, status = DeliveryStatus.SENT) }
        )

        verify(exactly = 3) { entityManager.persist(any<MessageDeliveryStatusJpaEntity>()) }
        verify(exactly = 0) { deliveryStatusRepo.save(any()) }
    }

    @Test
    fun `should do nothing when there are no delivery rows to write`() {
        adapter.saveDeliveryStatuses(emptyList())

        verify(exactly = 0) { entityManager.persist(any()) }
    }
}

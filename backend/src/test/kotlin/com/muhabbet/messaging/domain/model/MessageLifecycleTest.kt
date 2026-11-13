package com.muhabbet.messaging.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** The scheduled-message lifecycle of #872: Scheduled(at) -> Live on delivery, and nothing else moves. */
class MessageLifecycleTest {

    private val writtenAt = Instant.parse("2026-11-01T10:00:00Z")
    private val dueAt = Instant.parse("2026-11-08T10:00:00Z")

    private fun message(isScheduled: Boolean = true, isDeleted: Boolean = false) = Message(
        id = UUID.randomUUID(),
        conversationId = UUID.randomUUID(),
        senderId = UUID.randomUUID(),
        content = "haftaya",
        clientTimestamp = writtenAt,
        serverTimestamp = writtenAt,
        scheduledAt = dueAt,
        isScheduled = isScheduled,
        isDeleted = isDeleted
    )

    @Test
    fun `should read its lifecycle from the stored flags`() {
        assertEquals(MessageLifecycle.Scheduled(dueAt), message().lifecycle)
        assertEquals(MessageLifecycle.Live, message(isScheduled = false).lifecycle)
        assertEquals(MessageLifecycle.Deleted, message(isDeleted = true).lifecycle)
    }

    @Test
    fun `should not deliver before it is due`() {
        assertEquals(ScheduledDelivery.NotDue, message().deliverDue(dueAt.minusSeconds(1), null))
    }

    @Test
    fun `should deliver at the due instant, re-stamped to delivery time`() {
        val due = message().deliverDue(dueAt, null) as ScheduledDelivery.Due

        assertFalse(due.message.isScheduled)
        assertEquals(dueAt, due.message.serverTimestamp)
        assertNull(due.message.expiresAt)
    }

    @Test
    fun `should count a disappearing window from delivery, not from scheduling`() {
        val deliveredAt = dueAt.plusSeconds(30)
        val due = message().deliverDue(deliveredAt, 86_400) as ScheduledDelivery.Due

        assertEquals(deliveredAt.plusSeconds(86_400), due.message.expiresAt)
    }

    @Test
    fun `should refuse to deliver a live or deleted message`() {
        assertEquals(ScheduledDelivery.NotScheduled, message(isScheduled = false).deliverDue(dueAt, null))
        assertEquals(ScheduledDelivery.NotScheduled, message(isDeleted = true).deliverDue(dueAt, null))
    }
}

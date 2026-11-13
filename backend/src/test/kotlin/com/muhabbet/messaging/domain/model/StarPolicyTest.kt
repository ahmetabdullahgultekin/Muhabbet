package com.muhabbet.messaging.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class StarPolicyTest {

    private val caller = UUID.randomUUID()
    private val other = UUID.randomUUID()

    private fun message(
        sender: UUID = other,
        isDeleted: Boolean = false,
        viewOnce: Boolean = false,
        isScheduled: Boolean = false
    ) = Message(
        id = UUID.randomUUID(),
        conversationId = UUID.randomUUID(),
        senderId = sender,
        content = "c",
        clientTimestamp = Instant.now(),
        isDeleted = isDeleted,
        viewOnce = viewOnce,
        isScheduled = isScheduled
    )

    @Test
    fun `should allow a live ordinary message for a member`() {
        assertEquals(StarDecision.Allowed, StarPolicy.decide(message(), callerIsMember = true, callerId = caller))
    }

    @Test
    fun `should hide a message that does not exist`() {
        assertEquals(StarDecision.NotVisible, StarPolicy.decide(null, callerIsMember = false, callerId = caller))
    }

    @Test
    fun `should hide a message from a non-member exactly as if it did not exist`() {
        assertEquals(StarDecision.NotVisible, StarPolicy.decide(message(), callerIsMember = false, callerId = caller))
    }

    @Test
    fun `should hide a non-member even when the message is deleted or view-once`() {
        // Membership is decided first, so the answer reveals nothing about the message's state.
        assertEquals(
            StarDecision.NotVisible,
            StarPolicy.decide(message(isDeleted = true), callerIsMember = false, callerId = caller)
        )
        assertEquals(
            StarDecision.NotVisible,
            StarPolicy.decide(message(viewOnce = true), callerIsMember = false, callerId = caller)
        )
    }

    @Test
    fun `should hide somebody else's scheduled message that is not due yet`() {
        assertEquals(
            StarDecision.NotVisible,
            StarPolicy.decide(message(isScheduled = true), callerIsMember = true, callerId = caller)
        )
    }

    @Test
    fun `should refuse the caller's own scheduled message as not starrable`() {
        assertEquals(
            StarDecision.NotStarrable,
            StarPolicy.decide(message(sender = caller, isScheduled = true), callerIsMember = true, callerId = caller)
        )
    }

    @Test
    fun `should refuse a view-once message as not starrable`() {
        assertEquals(
            StarDecision.NotStarrable,
            StarPolicy.decide(message(viewOnce = true), callerIsMember = true, callerId = caller)
        )
    }

    @Test
    fun `should report a deleted message as already deleted to a member`() {
        assertEquals(
            StarDecision.AlreadyDeleted,
            StarPolicy.decide(message(isDeleted = true), callerIsMember = true, callerId = caller)
        )
    }

    @Test
    fun `should round-trip a cursor through its wire form`() {
        val cursor = StarredCursor(Instant.parse("2026-11-13T10:15:30.123456Z"), UUID.randomUUID())

        assertEquals(cursor, StarredCursor.parse(cursor.encode()))
    }

    @Test
    fun `should reject a cursor this server did not produce`() {
        assertNull(StarredCursor.parse("garbage"))
        assertNull(StarredCursor.parse("2026-11-13T10:15:30Z_not-a-uuid"))
        assertNull(StarredCursor.parse("yesterday_" + UUID.randomUUID()))
        assertNull(StarredCursor.parse("2026-11-13T10:15:30Z_" + UUID.randomUUID() + "_x"))
    }
}

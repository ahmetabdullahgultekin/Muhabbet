package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant

/**
 * The rule that #513 was missing entirely.
 *
 * There was nothing to test before this: no `expiresAt` reached the client, so the only thing that
 * ever removed an expired message was leaving the chat and re-fetching it. A message could sit on
 * screen for a full minute after the server had deleted it, which is exactly what the person who
 * just set a 30-second timer sits and watches.
 */
class MessageExpiryTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun message(id: String, expiresAt: Instant?) = Message(
        id = id,
        conversationId = "conv-1",
        senderId = "user-1",
        contentType = ContentType.TEXT,
        content = "merhaba",
        clientTimestamp = now,
        expiresAt = expiresAt
    )

    @Test
    fun `should keep message when it has no deadline`() {
        val m = message("m1", expiresAt = null)
        assertFalse(m.hasExpiredBy(now))
        assertEquals(listOf(m), listOf(m).dropExpired(now))
    }

    @Test
    fun `should keep message when deadline is still in the future`() {
        val m = message("m1", expiresAt = now + 30.seconds)
        assertFalse(m.hasExpiredBy(now))
    }

    /**
     * The direction that matters. Removing a message before the server has is the failure that
     * reads as broken — it vanishes and then comes back on the next reload — so the grace window
     * exists to make an out-of-step clock err towards keeping it a moment too long.
     */
    @Test
    fun `should keep message when deadline just passed but is inside the clock grace`() {
        val m = message("m1", expiresAt = now - 1.seconds)
        assertFalse(m.hasExpiredBy(now))
    }

    @Test
    fun `should remove message when deadline passed by more than the clock grace`() {
        val m = message("m1", expiresAt = now - EXPIRY_CLOCK_GRACE - 1.seconds)
        assertTrue(m.hasExpiredBy(now))
    }

    @Test
    fun `should drop only the expired messages when the list is mixed`() {
        val kept = message("kept", expiresAt = now + 60.seconds)
        val permanent = message("permanent", expiresAt = null)
        val gone = message("gone", expiresAt = now - 1.minutesAsSeconds())

        val remaining = listOf(kept, permanent, gone).dropExpired(now)

        assertEquals(listOf("kept", "permanent"), remaining.map { it.id })
    }

    @Test
    fun `should report no next expiry when nothing in the list disappears`() {
        assertNull(listOf(message("m1", null), message("m2", null)).nextExpiryAt())
    }

    /**
     * The soonest deadline, not the next one still ahead: one timer serves the whole list, so it
     * must wake for whichever message goes first.
     */
    @Test
    fun `should report the earliest deadline when several messages will expire`() {
        val messages = listOf(
            message("late", expiresAt = now + 90.seconds),
            message("soon", expiresAt = now + 10.seconds),
            message("never", expiresAt = null)
        )

        assertEquals(now + 10.seconds + EXPIRY_CLOCK_GRACE, messages.nextExpiryAt())
    }

    /**
     * A deadline already in the past is still the answer. The caller turns it into a non-positive
     * `delay`, which does not wait — that is how a message fetched after its deadline but before
     * the server's once-a-minute sweep reached it gets removed immediately rather than lingering
     * until something else happens to change the list.
     */
    @Test
    fun `should report a past deadline rather than skipping to a future one`() {
        val messages = listOf(
            message("overdue", expiresAt = now - 120.seconds),
            message("later", expiresAt = now + 30.seconds)
        )

        val next = messages.nextExpiryAt()

        assertTrue(next != null && next < now, "expected the overdue deadline, got $next")
    }

    private fun Int.minutesAsSeconds() = (this * 60).seconds
}

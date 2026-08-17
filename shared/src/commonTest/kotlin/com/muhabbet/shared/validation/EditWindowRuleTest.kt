package com.muhabbet.shared.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule both halves now read (#597).
 *
 * It lives here rather than in `MessageService` because the server enforced fifteen minutes and the
 * app knew nothing about it — so the app offered *Düzenle* on a week-old message, let the user
 * retype it, and only then failed. These tests exist to keep the two answers identical; a copy of
 * `15` in the app would pass its own tests and still disagree with the server the first time this
 * number changed.
 *
 * Epoch milliseconds on both sides on purpose: the backend holds `java.time.Instant` and the app
 * holds `kotlinx.datetime.Instant`, and a shared rule cannot depend on either.
 */
class EditWindowRuleTest {

    private val minute = 60_000L
    private val now = 1_776_000_000_000L

    @Test
    fun `a message just sent can be edited`() {
        assertTrue(ValidationRules.isWithinEditWindow(sentAtEpochMillis = now, nowEpochMillis = now))
    }

    @Test
    fun `a message inside the window can be edited`() {
        val sentAt = now - 14 * minute
        assertTrue(ValidationRules.isWithinEditWindow(sentAt, now))
    }

    /**
     * Exactly fifteen minutes is still editable. The server's comparison was `> WINDOW`, so the
     * boundary was inclusive there; if this rule made it exclusive the app would hide an action the
     * server would still have honoured — which is the same disagreement, pointed the other way.
     */
    @Test
    fun `exactly at the window the message can still be edited`() {
        val sentAt = now - ValidationRules.MESSAGE_EDIT_WINDOW_MINUTES * minute
        assertTrue(ValidationRules.isWithinEditWindow(sentAt, now))
    }

    @Test
    fun `one minute past the window the message cannot be edited`() {
        val sentAt = now - (ValidationRules.MESSAGE_EDIT_WINDOW_MINUTES + 1) * minute
        assertFalse(ValidationRules.isWithinEditWindow(sentAt, now))
    }

    @Test
    fun `a message from last week cannot be edited`() {
        val sentAt = now - 7 * 24 * 60 * minute
        assertFalse(ValidationRules.isWithinEditWindow(sentAt, now))
    }

    /**
     * A device with a clock running fast stamps a message in the future. Treating that as editable
     * is deliberate: refusing it would block a legitimate edit because of a condition the user
     * cannot see and cannot fix.
     */
    @Test
    fun `a message timestamped in the future is editable rather than rejected`() {
        val sentAt = now + 5 * minute
        assertTrue(ValidationRules.isWithinEditWindow(sentAt, now))
    }

    @Test
    fun `the window is the fifteen minutes the server has always enforced`() {
        assertTrue(ValidationRules.MESSAGE_EDIT_WINDOW_MINUTES == 15L)
    }
}

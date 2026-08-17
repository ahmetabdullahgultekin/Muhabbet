package com.muhabbet.app.ui.chat

import com.muhabbet.designsystem.theme.MuhabbetGestures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards #601: releasing the record button used to have exactly one outcome, sending. The one part
 * of "does this release cancel or stop" that does not require real touch hardware is the arithmetic
 * threshold itself, so that is what is pinned here — the drag gesture around it can only be
 * exercised on a device.
 */
class VoiceRecordingPhaseTest {

    @Test
    fun should_not_cancel_when_the_finger_never_left_the_button() {
        assertFalse(isVoiceRecordingCancelledAt(0f))
    }

    @Test
    fun should_not_cancel_when_dragged_right_no_matter_how_far() {
        // Positive dragX is away from the cancel direction entirely; it must never read as armed.
        assertFalse(isVoiceRecordingCancelledAt(MuhabbetGestures.VoiceCancelThresholdPx * 10f))
    }

    @Test
    fun should_not_cancel_just_short_of_the_threshold() {
        assertFalse(isVoiceRecordingCancelledAt(-MuhabbetGestures.VoiceCancelThresholdPx + 1f))
    }

    @Test
    fun should_cancel_exactly_at_the_threshold() {
        assertTrue(isVoiceRecordingCancelledAt(-MuhabbetGestures.VoiceCancelThresholdPx))
    }

    @Test
    fun should_cancel_when_dragged_past_the_threshold() {
        assertTrue(isVoiceRecordingCancelledAt(-MuhabbetGestures.VoiceCancelThresholdPx - 1f))
    }
}

package com.muhabbet.app.ui.chat

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.muhabbet.designsystem.theme.MuhabbetGestures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards #601: releasing the record button used to have exactly one outcome, sending. The one part
 * of "does this release cancel or stop" that does not require real touch hardware is the arithmetic
 * threshold itself, so that is what is pinned here — the drag gesture around it can only be
 * exercised on a device.
 *
 * Every case runs at 1.0x and at 3.5x (#841): the threshold is a Dp, so the decision must be the
 * same for the same physical distance whatever the screen. When it was a px float, 140 px was 40dp
 * on a 3.5x phone and 140dp on an mdpi one.
 */
class VoiceRecordingPhaseTest {

    private val densities = listOf(Density(1f), Density(3.5f))

    private fun Density.thresholdPx(): Float = MuhabbetGestures.VoiceCancelThreshold.toPx()

    @Test
    fun should_not_cancel_when_the_finger_never_left_the_button() = densities.forEach { d ->
        assertFalse(isVoiceRecordingCancelledAt(0f, d))
    }

    @Test
    fun should_not_cancel_when_dragged_right_no_matter_how_far() = densities.forEach { d ->
        // Positive dragX is away from the cancel direction entirely; it must never read as armed.
        assertFalse(isVoiceRecordingCancelledAt(d.thresholdPx() * 10f, d))
    }

    @Test
    fun should_not_cancel_just_short_of_the_threshold() = densities.forEach { d ->
        assertFalse(isVoiceRecordingCancelledAt(-d.thresholdPx() + 1f, d))
    }

    @Test
    fun should_cancel_exactly_at_the_threshold() = densities.forEach { d ->
        assertTrue(isVoiceRecordingCancelledAt(-d.thresholdPx(), d))
    }

    @Test
    fun should_cancel_when_dragged_past_the_threshold() = densities.forEach { d ->
        assertTrue(isVoiceRecordingCancelledAt(-d.thresholdPx() - 1f, d))
    }

    @Test
    fun should_cancel_at_the_same_physical_distance_when_the_density_changes() {
        // 60dp of drag: short of the threshold at every density. In px that is 60 on mdpi and 210 on
        // a 3.5x phone — the 210 px drag used to cancel, because 210 > the old fixed 140 px.
        densities.forEach { d ->
            val sixtyDp = with(d) { 60.dp.toPx() }
            assertFalse(isVoiceRecordingCancelledAt(-sixtyDp, d), "60dp must not cancel at density ${d.density}")
        }
        assertEquals(
            with(Density(3.5f)) { MuhabbetGestures.VoiceCancelThreshold.toPx() },
            with(Density(1f)) { MuhabbetGestures.VoiceCancelThreshold.toPx() } * 3.5f
        )
    }
}

package com.muhabbet.app.ui.chat

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.muhabbet.designsystem.theme.MuhabbetGestures
import com.muhabbet.designsystem.theme.MuhabbetMotion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the crash that killed release 0.3.0 on the first swipe-to-reply:
 * `IllegalArgumentException: Padding must be non-negative`.
 *
 * Two facts have to hold together for the bug to be gone, so both are asserted. The first is a
 * property of the design system and is expected to stay true — the spatial springs are deliberately
 * under-damped. The second is the fix: because the spring undershoots, the state it drives has to
 * declare its own domain, or a reader with a non-negative precondition will be handed a negative.
 */
class SwipeReplyOffsetTest {

    @Test
    fun should_settle_from_below_the_target_when_a_spatial_spring_returns_to_rest() {
        val springBack = TargetBasedAnimation(
            animationSpec = MuhabbetMotion.spatialDefault(),
            typeConverter = Float.VectorConverter,
            initialValue = MuhabbetGestures.SwipeReplyMax,
            targetValue = 0f
        )

        val lowest = (0L..springBack.durationNanos step SampleIntervalNanos)
            .minOf { springBack.getValueFromNanos(it) }

        assertTrue(
            lowest < 0f,
            "MuhabbetMotion.spatialDefault() is under-damped by design, so a return to 0f must " +
                "cross below it. Lowest sampled value was $lowest. If this ever stops being true " +
                "the damping changed, and the reason the offset below is bounded changed with it."
        )
    }

    @Test
    fun should_never_leave_its_domain_when_the_swipe_offset_is_driven_out_of_range() = runTest {
        val offset = swipeReplyOffset(MuhabbetGestures.SwipeReplyMax)

        offset.snapTo(-1f)
        assertEquals(
            0f,
            offset.value,
            "A negative swipe offset reaches Modifier.padding(start = …), whose element " +
                "constructor rejects a negative Dp and crashes the app."
        )

        offset.snapTo(MuhabbetGestures.SwipeReplyMax + 50f)
        assertEquals(
            MuhabbetGestures.SwipeReplyMax,
            offset.value,
            "The bubble must not shift further than the gesture token allows."
        )
    }
}

/** Half a frame at 60 Hz: fine enough that a ~270 ms undershoot cannot be sampled over. */
private const val SampleIntervalNanos = 8_000_000L

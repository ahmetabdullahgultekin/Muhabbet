package com.muhabbet.designsystem.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.muhabbet.designsystem.platform.isReducedMotionScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The reduced-motion policy (#842). `EnterTransition` and `ExitTransition` compare by what they
 * contain, so "no slide under reduced motion" is checked as equality with a bare fade.
 */
class MotionPolicyTest {

    @Test
    fun should_enter_with_a_fade_only_when_motion_is_reduced() {
        assertEquals(fadeIn(MuhabbetMotion.effectsFast()), MuhabbetMotion.enterFadeUp(reduced = true))
    }

    @Test
    fun should_enter_with_a_fade_and_a_rise_when_motion_is_not_reduced() {
        assertNotEquals(fadeIn(MuhabbetMotion.effectsFast()), MuhabbetMotion.enterFadeUp(reduced = false))
    }

    @Test
    fun should_exit_with_a_fade_only_when_motion_is_reduced() {
        assertEquals(fadeOut(MuhabbetMotion.effectsFast()), MuhabbetMotion.exitFadeDown(reduced = true))
        assertNotEquals(fadeOut(MuhabbetMotion.effectsFast()), MuhabbetMotion.exitFadeDown(reduced = false))
    }

    @Test
    fun should_pop_with_a_fade_only_when_motion_is_reduced() {
        assertEquals(fadeIn(MuhabbetMotion.effectsFast()), MuhabbetMotion.enterPop(reduced = true))
    }

    @Test
    fun should_snap_a_spatial_change_when_motion_is_reduced() {
        assertIs<SnapSpec<Float>>(MuhabbetMotion.spatialDefault<Float>(reduced = true))
        assertIs<SpringSpec<Float>>(MuhabbetMotion.spatialDefault<Float>(reduced = false))
    }

    @Test
    fun should_treat_only_an_animator_scale_of_zero_as_reduced_motion() {
        assertTrue(isReducedMotionScale(0f))
        assertFalse(isReducedMotionScale(0.5f), "0.5x is a developer speeding animations up, not a request to remove them")
        assertFalse(isReducedMotionScale(1f))
        assertFalse(isReducedMotionScale(10f))
    }
}

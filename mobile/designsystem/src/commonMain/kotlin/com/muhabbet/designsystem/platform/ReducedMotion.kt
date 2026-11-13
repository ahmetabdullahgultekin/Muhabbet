package com.muhabbet.designsystem.platform

import androidx.compose.runtime.Composable

/**
 * Whether the operating system asks for reduced motion, observed live (#842).
 *
 * - **Android** has no dedicated "reduce motion" switch. The accessibility setting *Remove
 *   animations* (and the developer option of the same effect) sets
 *   `Settings.Global.ANIMATOR_DURATION_SCALE` to 0, and that is the signal every platform widget
 *   honours, so it is the one read here.
 * - **iOS** exposes it directly: `UIAccessibilityIsReduceMotionEnabled()`, with
 *   `UIAccessibilityReduceMotionStatusDidChangeNotification` posted on change.
 *
 * Both actuals re-read on change rather than once, because the user can flip the setting while the
 * app is open and the next animation should already respect it.
 *
 * Read only by [com.muhabbet.designsystem.theme.MuhabbetTheme], which publishes the answer as
 * [com.muhabbet.designsystem.theme.LocalReducedMotion]; components read the local, never this.
 */
@Composable
internal expect fun rememberSystemReducedMotion(): Boolean

/**
 * The Android decision, as a pure function so it is testable without a device: only an animator
 * duration scale of exactly zero means "no animation". 0.5× is a developer speeding things up, not
 * an accessibility request.
 */
internal fun isReducedMotionScale(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

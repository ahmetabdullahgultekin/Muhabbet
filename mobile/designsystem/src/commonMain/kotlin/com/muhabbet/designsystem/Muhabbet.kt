package com.muhabbet.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.LocalTextStyles
import com.muhabbet.designsystem.theme.LocalThemeMode
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetDurations
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetGestures
import com.muhabbet.designsystem.theme.MuhabbetHaptics
import com.muhabbet.designsystem.theme.MuhabbetIcons
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetTextStyles
import com.muhabbet.designsystem.theme.ResolvedThemeMode

/**
 * One import for the whole design system.
 *
 * `import com.muhabbet.designsystem.Muhabbet` then `Muhabbet.colors.bubbleOwn.container`,
 * `Muhabbet.spacing.Large`, `Muhabbet.motion.spatialDefault()`, `Muhabbet.icons.Back`.
 *
 * Screens previously needed a separate import per token object — 178 import lines across 61 files
 * for what is conceptually one thing. The individual objects stay public, so nothing has to migrate
 * at once and existing `MuhabbetSpacing.Large` call sites keep working; this is the front door, not
 * a replacement.
 *
 * Composable-backed properties are `@ReadOnlyComposable` so reading a token does not itself cause
 * recomposition bookkeeping.
 */
object Muhabbet {

    /** Roles Material 3 has no name for: bubbles, ticks, presence, wallpaper, scrim. */
    val colors: MuhabbetSemanticColors
        @Composable @ReadOnlyComposable get() = LocalSemanticColors.current

    /** Messaging text styles that sit between the Material roles: bubble body, list row, metadata. */
    val text: MuhabbetTextStyles
        @Composable @ReadOnlyComposable get() = LocalTextStyles.current

    /** Which variant is actually rendering, after System has been resolved against the OS. */
    val themeMode: ResolvedThemeMode
        @Composable @ReadOnlyComposable get() = LocalThemeMode.current

    /** Semantic haptic intents. Respects the user's preference; no-op outside the theme. */
    val haptics: MuhabbetHaptics
        @Composable @ReadOnlyComposable get() = LocalHaptics.current

    // Constant token sets — no composition needed, so callable from anywhere.
    val spacing get() = MuhabbetSpacing
    val sizes get() = MuhabbetSizes
    val corners get() = MuhabbetCorners
    val elevation get() = MuhabbetElevation
    val durations get() = MuhabbetDurations
    val gestures get() = MuhabbetGestures
    val motion get() = MuhabbetMotion
    val icons get() = MuhabbetIcons
}

package com.muhabbet.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muhabbet.designsystem.platform.SystemBarsEffect

// ─── Semantic colors (beyond M3 colorScheme) ────────────────

/** Opacity of the control bars drawn over the full-screen media viewer's backdrop. */
private const val ScrimOverlayAlpha = 0.5f

data class MuhabbetSemanticColors(
    val statusOnline: Color,
    val statusRead: Color,
    val statusDelivered: Color,
    val statusSending: Color,
    val callDecline: Color,
    val onCallDecline: Color,
    val callAccept: Color,
    val onCallAccept: Color,
    val callMissed: Color,
    val bubbleOwn: Color,
    val bubbleOther: Color,
    val onBubbleOwn: Color,
    val onBubbleOther: Color,
    val linkColor: Color,
    val chatWallpaper: Color,
    val inputBarBackground: Color,
    val inputFieldBackground: Color,
    val dividerColor: Color,
    val secondaryText: Color,
    val unreadBadge: Color,
    val onUnreadBadge: Color,
    /**
     * Backdrop of the immersive full-screen media viewer. Deliberately identical in every
     * variant — the viewer is theme-independent so that photos are judged against black.
     */
    val scrim: Color,
    /** Translucent bar drawn over the [scrim] to carry the viewer's controls. */
    val scrimOverlay: Color,
    /** Icons and labels drawn on [scrim] / [scrimOverlay]. */
    val onScrim: Color
)

val LightSemanticColors = MuhabbetSemanticColors(
    statusOnline = MuhabbetPalette.UnreadLight,
    statusRead = MuhabbetPalette.ReadTickLight,
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFFBDBDBD),
    callDecline = Color(0xFFE53935),
    onCallDecline = Color.White,
    callAccept = Color(0xFF43A047),
    onCallAccept = Color.White,
    callMissed = Color(0xFFE53935),
    bubbleOwn = MuhabbetPalette.OwnBubbleLight,
    bubbleOther = Color.White,
    onBubbleOwn = MuhabbetPalette.TextPrimaryLight,
    onBubbleOther = MuhabbetPalette.TextPrimaryLight,
    linkColor = MuhabbetPalette.Accent,
    chatWallpaper = MuhabbetPalette.WallpaperLight,
    inputBarBackground = Color.White,
    inputFieldBackground = MuhabbetPalette.InputFieldLight,
    dividerColor = MuhabbetPalette.DividerLight,
    secondaryText = MuhabbetPalette.TextSecondaryLight,
    unreadBadge = MuhabbetPalette.UnreadLight,
    onUnreadBadge = Color.White,
    scrim = Color.Black,
    scrimOverlay = Color.Black.copy(alpha = ScrimOverlayAlpha),
    onScrim = Color.White
)

val DarkSemanticColors = MuhabbetSemanticColors(
    statusOnline = MuhabbetPalette.Accent,
    statusRead = MuhabbetPalette.ReadTickDark,
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFF757575),
    callDecline = Color(0xFFEF5350),
    onCallDecline = Color.White,
    callAccept = MuhabbetPalette.Accent,
    onCallAccept = Color.White,
    callMissed = Color(0xFFEF5350),
    bubbleOwn = MuhabbetPalette.OwnBubbleDark,
    bubbleOther = MuhabbetPalette.DarkSurface,
    onBubbleOwn = MuhabbetPalette.TextPrimary,
    onBubbleOther = MuhabbetPalette.TextPrimary,
    linkColor = MuhabbetPalette.Accent,
    chatWallpaper = MuhabbetPalette.WallpaperDark,
    inputBarBackground = MuhabbetPalette.DarkSurface,
    inputFieldBackground = MuhabbetPalette.DarkElevated,
    dividerColor = MuhabbetPalette.DividerDark,
    secondaryText = MuhabbetPalette.TextSecondary,
    unreadBadge = MuhabbetPalette.Accent,
    onUnreadBadge = Color.White,
    scrim = Color.Black,
    scrimOverlay = Color.Black.copy(alpha = ScrimOverlayAlpha),
    onScrim = Color.White
)

val OledSemanticColors = MuhabbetSemanticColors(
    statusOnline = MuhabbetPalette.Accent,
    statusRead = MuhabbetPalette.ReadTickDark,
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFF757575),
    callDecline = Color(0xFFEF5350),
    onCallDecline = Color.White,
    callAccept = MuhabbetPalette.Accent,
    onCallAccept = Color.White,
    callMissed = Color(0xFFEF5350),
    bubbleOwn = MuhabbetPalette.OwnBubbleDark,
    bubbleOther = Color(0xFF0A1014),
    onBubbleOwn = MuhabbetPalette.TextPrimary,
    onBubbleOther = MuhabbetPalette.TextPrimary,
    linkColor = MuhabbetPalette.Accent,
    chatWallpaper = Color.Black,
    inputBarBackground = Color(0xFF0A1014),
    inputFieldBackground = Color(0xFF1A2228),
    dividerColor = Color(0xFF1A2228),
    secondaryText = MuhabbetPalette.TextSecondary,
    unreadBadge = MuhabbetPalette.Accent,
    onUnreadBadge = Color.White,
    scrim = Color.Black,
    scrimOverlay = Color.Black.copy(alpha = ScrimOverlayAlpha),
    onScrim = Color.White
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

// ─── Spacing tokens ─────────────────────────────────────────

object MuhabbetSpacing {
    /** No gap. Named so that "deliberately flush" is distinguishable from "forgot to set it". */
    val None: Dp = 0.dp
    val XSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val XLarge: Dp = 24.dp
    val XXLarge: Dp = 32.dp
}

// ─── Size tokens ────────────────────────────────────────────

object MuhabbetSizes {
    val MinTouchTarget: Dp = 48.dp
    val IconSmall: Dp = 16.dp
    val IconMedium: Dp = 20.dp
    val IconLarge: Dp = 24.dp

    /** An icon carrying a state on its own, not labelling something else — e.g. the view-once eye. */
    val IconHero: Dp = 32.dp

    /** The large, faded icon above an empty- or error-state message. */
    val IconEmptyState: Dp = 56.dp

    /** Height of a placeholder text line in a skeleton — matches a body line's visual weight. */
    val SkeletonLine: Dp = 14.dp

    /** The secondary placeholder line, thinner so the two do not read as a progress bar. */
    val SkeletonLineSmall: Dp = 12.dp

    // Avatar sizes
    val AvatarXSmall: Dp = 36.dp
    val AvatarSmall: Dp = 40.dp
    val AvatarChatList: Dp = 52.dp
    val AvatarChatBar: Dp = 42.dp
    val AvatarMedium: Dp = 48.dp
    val AvatarLarge: Dp = 56.dp
    val AvatarXLarge: Dp = 72.dp
    val AvatarXXLarge: Dp = 80.dp
    val AvatarHero: Dp = 96.dp
    val AvatarCall: Dp = 120.dp

    // Bubble dimensions
    val BubbleMinWidth: Dp = 80.dp

    /**
     * Widest a chat bubble may grow. A fixed cap rather than a fraction of the window:
     * `Modifier.fillMaxWidth(fraction)` pins a bubble to that width instead of capping it,
     * so short messages would render as wide as long ones.
     */
    val BubbleMaxWidth: Dp = 320.dp

    @Deprecated("Beta design uses uniform corners, no tail")
    val BubbleTailRadius: Dp = 4.dp
    val BubblePaddingHorizontal: Dp = 8.dp
    val BubblePaddingVertical: Dp = 6.dp
    val ImagePreviewMaxHeight: Dp = 200.dp
    val StickerSize: Dp = 150.dp

    /**
     * The sealed tile standing in for an unopened view-once message. Deliberately not a preview —
     * see the note in `ViewOnceBubble`.
     */
    val ViewOncePlaceholder: Dp = 120.dp

    // Chat list
    val ChatListItemMinHeight: Dp = 72.dp
    val ChatListDividerInset: Dp = 84.dp
}

// ─── Duration tokens ────────────────────────────────────────

object MuhabbetDurations {
    const val TypingTimeoutMs: Long = 3000L
    const val StatusDisplayMs: Long = 5000L
    const val StatusProgressTickMs: Long = 50L
    const val CallTimerTickMs: Long = 1000L
    const val ShimmerDurationMs: Int = 1200
}

// ─── Gesture tokens ─────────────────────────────────────────

object MuhabbetGestures {
    const val SwipeReplyThreshold: Float = 80f
    const val SwipeReplyMax: Float = 120f
}

// ─── Elevation tokens ──────────────────────────────────────

object MuhabbetElevation {
    val None: Dp = 0.dp
    val Level1: Dp = 1.dp
    val Level2: Dp = 2.dp
    val Level3: Dp = 3.dp
    val Level4: Dp = 4.dp
    val Level5: Dp = 6.dp
    val Level6: Dp = 8.dp
}

@Composable
fun MuhabbetTheme(
    mode: MuhabbetThemeMode = MuhabbetThemeMode.System,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    // Only System consults the OS. Light/Dark/Oled are explicit user choices and are honoured
    // unconditionally — the previous `isDark && oledBlack` guard silently downgraded a user who
    // picked OLED on a light-mode phone all the way back to Light.
    val resolved = when (mode) {
        MuhabbetThemeMode.System -> if (isSystemInDarkTheme()) ResolvedThemeMode.Dark else ResolvedThemeMode.Light
        MuhabbetThemeMode.Light -> ResolvedThemeMode.Light
        MuhabbetThemeMode.Dark -> ResolvedThemeMode.Dark
        MuhabbetThemeMode.Oled -> ResolvedThemeMode.Oled
    }
    val colorScheme = when (resolved) {
        ResolvedThemeMode.Light -> MuhabbetLightColorScheme
        ResolvedThemeMode.Dark -> MuhabbetDarkColorScheme
        ResolvedThemeMode.Oled -> MuhabbetOledBlackColorScheme
    }
    val semanticColors = when (resolved) {
        ResolvedThemeMode.Light -> LightSemanticColors
        ResolvedThemeMode.Dark -> DarkSemanticColors
        ResolvedThemeMode.Oled -> OledSemanticColors
    }

    // Status- and navigation-bar icons are painted by the OS, outside the Compose tree, so they
    // cannot pick up the scheme by themselves. Light icons everywhere except the light theme.
    SystemBarsEffect(lightIcons = resolved != ResolvedThemeMode.Light)

    // Provided from here so that every component gets haptics for free and the user's on/off
    // preference is checked in exactly one place rather than at each call site.
    val haptics = rememberMuhabbetHaptics(enabled = hapticsEnabled)

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalThemeMode provides resolved,
        LocalHaptics provides haptics
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MuhabbetTypography,
            shapes = MuhabbetShapes,
            content = content
        )
    }
}

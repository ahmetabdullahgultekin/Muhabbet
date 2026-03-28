package com.muhabbet.app.ui.theme

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

// ─── WhatsApp-spec palette (March 2026) ─────────────────────

// Dark mode surfaces
private val WaDarkBg        = Color(0xFF111B21)  // main background
private val WaDarkSurface   = Color(0xFF1F2C34)  // cards, top bar, bottom nav
private val WaDarkElevated  = Color(0xFF2A3942)  // input field, dividers

// Light mode surfaces
private val WaLightBg       = Color(0xFFFFFFFF)
private val WaLightSurface  = Color(0xFFFFFFFF)
private val WaLightInput    = Color(0xFFF0F2F5)
private val WaLightWallpaper = Color(0xFFECE5DD)

// Accent greens
private val WaGreen         = Color(0xFF00A884)  // primary accent (2024+ redesign)
private val WaGreenClassic  = Color(0xFF25D366)  // classic green (light mode badge)

// Text
private val WaPrimaryText   = Color(0xFFE9EDEF)  // dark mode primary text
private val WaSecondaryText = Color(0xFF8696A0)  // timestamps, previews

// Bubbles
private val WaOwnBubbleDark  = Color(0xFF005C4B)
private val WaOtherBubbleDark = Color(0xFF1F2C34)
private val WaOwnBubbleLight  = Color(0xFFD9FDD3)
private val WaOtherBubbleLight = Color(0xFFFFFFFF)

// Ticks
private val WaTickReadDark  = Color(0xFF53BDEB)
private val WaTickReadLight = Color(0xFF4FB6EC)
private val WaTickGray      = Color(0xFF8696A0)

// Error
private val Red400 = Color(0xFFEF5350)
private val Red700 = Color(0xFFD32F2F)

// ─── Semantic colors (beyond M3 colorScheme) ────────────────

data class MuhabbetSemanticColors(
    val statusOnline: Color,
    val statusRead: Color,
    val statusDelivered: Color,
    val statusSending: Color,
    val callDecline: Color,
    val callAccept: Color,
    val callMissed: Color,
    val bubbleOwn: Color,
    val bubbleOther: Color,
    val onBubbleOwn: Color,
    val onBubbleOther: Color,
    val linkColor: Color
)

val LightSemanticColors = MuhabbetSemanticColors(
    statusOnline = WaGreenClassic,
    statusRead = WaTickReadLight,
    statusDelivered = WaTickGray,
    statusSending = WaTickGray,
    callDecline = Color(0xFFE53935),
    callAccept = WaGreen,
    callMissed = Color(0xFFE53935),
    bubbleOwn = WaOwnBubbleLight,
    bubbleOther = WaOtherBubbleLight,
    onBubbleOwn = Color(0xFF111B21),
    onBubbleOther = Color(0xFF111B21),
    linkColor = Color(0xFF027EB5)
)

val DarkSemanticColors = MuhabbetSemanticColors(
    statusOnline = WaGreen,
    statusRead = WaTickReadDark,
    statusDelivered = WaTickGray,
    statusSending = WaTickGray,
    callDecline = Red400,
    callAccept = WaGreen,
    callMissed = Red400,
    bubbleOwn = WaOwnBubbleDark,
    bubbleOther = WaOtherBubbleDark,
    onBubbleOwn = WaPrimaryText,
    onBubbleOther = WaPrimaryText,
    linkColor = Color(0xFF53BDEB)
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

// ─── Spacing tokens ─────────────────────────────────────────

object MuhabbetSpacing {
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

    // Avatar sizes
    val AvatarXSmall: Dp = 36.dp
    val AvatarSmall: Dp = 40.dp
    val AvatarMedium: Dp = 48.dp
    val AvatarLarge: Dp = 56.dp
    val AvatarXLarge: Dp = 72.dp
    val AvatarXXLarge: Dp = 80.dp
    val AvatarHero: Dp = 96.dp
    val AvatarCall: Dp = 120.dp

    // Bubble dimensions
    val BubbleMinWidth: Dp = 80.dp
    val BubbleCornerRadius: Dp = 16.dp
    val BubbleTailRadius: Dp = 4.dp
    val ImagePreviewMaxHeight: Dp = 200.dp
    val StickerSize: Dp = 150.dp
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

// ─── M3 Color schemes ──────────────────────────────────────

val MuhabbetLightColorScheme = lightColorScheme(
    primary = WaGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9FDD3),
    onPrimaryContainer = Color(0xFF002114),
    secondary = WaGreenClassic,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F5D8),
    onSecondaryContainer = Color(0xFF002111),
    error = Red700,
    onError = Color.White,
    background = WaLightBg,
    onBackground = Color(0xFF111B21),
    surface = WaLightSurface,
    onSurface = Color(0xFF111B21),
    surfaceVariant = WaLightInput,
    onSurfaceVariant = Color(0xFF667781),
    outline = Color(0xFFCCD0D4),
    outlineVariant = Color(0xFFE9EDEF)
)

val MuhabbetDarkColorScheme = darkColorScheme(
    primary = WaGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF00382B),
    onPrimaryContainer = Color(0xFFD9FDD3),
    secondary = WaGreenClassic,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF003820),
    onSecondaryContainer = Color(0xFFD9FDD3),
    error = Red400,
    onError = Color(0xFF601410),
    background = WaDarkBg,
    onBackground = WaPrimaryText,
    surface = WaDarkSurface,
    onSurface = WaPrimaryText,
    surfaceVariant = WaDarkElevated,
    onSurfaceVariant = WaSecondaryText,
    outline = WaSecondaryText,
    outlineVariant = WaDarkElevated
)

// AMOLED Black theme
val MuhabbetOledBlackColorScheme = darkColorScheme(
    primary = WaGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF00382B),
    onPrimaryContainer = Color(0xFFD9FDD3),
    secondary = WaGreenClassic,
    onSecondary = Color.White,
    error = Red400,
    onError = Color(0xFF601410),
    background = Color(0xFF000000),
    onBackground = WaPrimaryText,
    surface = Color(0xFF0A1014),
    onSurface = WaPrimaryText,
    surfaceVariant = Color(0xFF111B21),
    onSurfaceVariant = WaSecondaryText,
    outline = WaSecondaryText,
    outlineVariant = Color(0xFF111B21)
)

@Composable
fun MuhabbetTheme(
    oledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = when {
        isDark && oledBlack -> MuhabbetOledBlackColorScheme
        isDark -> MuhabbetDarkColorScheme
        else -> MuhabbetLightColorScheme
    }
    val semanticColors = if (isDark) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

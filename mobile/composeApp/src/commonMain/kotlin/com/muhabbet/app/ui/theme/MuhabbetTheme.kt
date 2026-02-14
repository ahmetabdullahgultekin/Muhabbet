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

// ─── Base palette ───────────────────────────────────────────

private val Teal700 = Color(0xFF00796B)
private val Teal800 = Color(0xFF00695C)
private val Teal50 = Color(0xFFE0F2F1)
private val Teal100 = Color(0xFFB2DFDB)
private val Teal200 = Color(0xFF80CBC4)
private val Green600 = Color(0xFF43A047)
private val Green100 = Color(0xFFC8E6C9)
private val Green300 = Color(0xFF81C784)
private val Amber600 = Color(0xFFFFB300)
private val Amber100 = Color(0xFFFFECB3)
private val Red700 = Color(0xFFD32F2F)
private val Red400 = Color(0xFFEF5350)

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
    statusOnline = Color(0xFF4CAF50),
    statusRead = Color(0xFF4FC3F7),
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFFBDBDBD),
    callDecline = Color(0xFFE53935),
    callAccept = Color(0xFF43A047),
    callMissed = Color(0xFFE53935),
    bubbleOwn = Color(0xFFDCF8C6),
    bubbleOther = Color.White,
    onBubbleOwn = Color(0xFF1C1B1F),
    onBubbleOther = Color(0xFF1C1B1F),
    linkColor = Color(0xFF1565C0)
)

val DarkSemanticColors = MuhabbetSemanticColors(
    statusOnline = Color(0xFF66BB6A),
    statusRead = Color(0xFF4FC3F7),
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFF757575),
    callDecline = Color(0xFFEF5350),
    callAccept = Color(0xFF66BB6A),
    callMissed = Color(0xFFEF5350),
    bubbleOwn = Color(0xFF005D4B),
    bubbleOther = Color(0xFF2C2C2E),
    onBubbleOwn = Color(0xFFE6E1E5),
    onBubbleOther = Color(0xFFE6E1E5),
    linkColor = Color(0xFF64B5F6)
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
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal800,
    secondary = Green600,
    onSecondary = Color.White,
    secondaryContainer = Green100,
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Amber600,
    onTertiary = Color.White,
    tertiaryContainer = Amber100,
    onTertiaryContainer = Color(0xFF7F6003),
    error = Red700,
    onError = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF0F4F3),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

val MuhabbetDarkColorScheme = darkColorScheme(
    primary = Teal200,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Teal100,
    secondary = Green300,
    onSecondary = Color(0xFF003A08),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Green100,
    tertiary = Amber600,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Amber100,
    error = Red400,
    onError = Color(0xFF601410),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

// OLED Black theme — true black backgrounds for AMOLED displays
val MuhabbetOledBlackColorScheme = darkColorScheme(
    primary = Teal200,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF003D36),
    onPrimaryContainer = Teal100,
    secondary = Green300,
    onSecondary = Color(0xFF003A08),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Green100,
    tertiary = Amber600,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Amber100,
    error = Red400,
    onError = Color(0xFF601410),
    surface = Color.Black,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF161618),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    background = Color.Black,
    onBackground = Color(0xFFE6E1E5)
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

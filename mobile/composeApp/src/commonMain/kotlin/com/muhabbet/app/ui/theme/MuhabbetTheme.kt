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

// ─── WhatsApp-aligned palette ──────────────────────────────

private val WhatsAppAccent = Color(0xFF00A884)
private val WhatsAppDarkBg = Color(0xFF111B21)
private val WhatsAppDarkSurface = Color(0xFF1F2C34)
private val WhatsAppDarkElevated = Color(0xFF2A3942)
private val WhatsAppWallpaperDark = Color(0xFF0D1418)
private val WhatsAppOwnBubbleDark = Color(0xFF005C4B)
private val WhatsAppTextPrimary = Color(0xFFE9EDEF)
private val WhatsAppTextSecondary = Color(0xFF8696A0)
private val WhatsAppReadTickDark = Color(0xFF53BDEB)
private val WhatsAppOwnBubbleLight = Color(0xFFD9FDD3)
private val WhatsAppWallpaperLight = Color(0xFFECE5DD)
private val WhatsAppUnreadLight = Color(0xFF25D366)
private val WhatsAppTextPrimaryLight = Color(0xFF111B21)
private val WhatsAppTextSecondaryLight = Color(0xFF667781)
private val WhatsAppReadTickLight = Color(0xFF4FB6EC)
private val WhatsAppInputFieldLight = Color(0xFFF0F2F5)
private val WhatsAppDividerLight = Color(0xFFE9EDEF)
private val WhatsAppDividerDark = Color(0xFF2A3942)

// Retained for error states
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
    val linkColor: Color,
    val chatWallpaper: Color,
    val inputBarBackground: Color,
    val inputFieldBackground: Color,
    val dividerColor: Color,
    val secondaryText: Color,
    val unreadBadge: Color
)

val LightSemanticColors = MuhabbetSemanticColors(
    statusOnline = WhatsAppUnreadLight,
    statusRead = WhatsAppReadTickLight,
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFFBDBDBD),
    callDecline = Color(0xFFE53935),
    callAccept = Color(0xFF43A047),
    callMissed = Color(0xFFE53935),
    bubbleOwn = WhatsAppOwnBubbleLight,
    bubbleOther = Color.White,
    onBubbleOwn = WhatsAppTextPrimaryLight,
    onBubbleOther = WhatsAppTextPrimaryLight,
    linkColor = WhatsAppAccent,
    chatWallpaper = WhatsAppWallpaperLight,
    inputBarBackground = Color.White,
    inputFieldBackground = WhatsAppInputFieldLight,
    dividerColor = WhatsAppDividerLight,
    secondaryText = WhatsAppTextSecondaryLight,
    unreadBadge = WhatsAppUnreadLight
)

val DarkSemanticColors = MuhabbetSemanticColors(
    statusOnline = WhatsAppAccent,
    statusRead = WhatsAppReadTickDark,
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFF757575),
    callDecline = Color(0xFFEF5350),
    callAccept = WhatsAppAccent,
    callMissed = Color(0xFFEF5350),
    bubbleOwn = WhatsAppOwnBubbleDark,
    bubbleOther = WhatsAppDarkSurface,
    onBubbleOwn = WhatsAppTextPrimary,
    onBubbleOther = WhatsAppTextPrimary,
    linkColor = WhatsAppAccent,
    chatWallpaper = WhatsAppWallpaperDark,
    inputBarBackground = WhatsAppDarkSurface,
    inputFieldBackground = WhatsAppDarkElevated,
    dividerColor = WhatsAppDividerDark,
    secondaryText = WhatsAppTextSecondary,
    unreadBadge = WhatsAppAccent
)

val OledSemanticColors = MuhabbetSemanticColors(
    statusOnline = WhatsAppAccent,
    statusRead = WhatsAppReadTickDark,
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFF757575),
    callDecline = Color(0xFFEF5350),
    callAccept = WhatsAppAccent,
    callMissed = Color(0xFFEF5350),
    bubbleOwn = WhatsAppOwnBubbleDark,
    bubbleOther = Color(0xFF0A1014),
    onBubbleOwn = WhatsAppTextPrimary,
    onBubbleOther = WhatsAppTextPrimary,
    linkColor = WhatsAppAccent,
    chatWallpaper = Color.Black,
    inputBarBackground = Color(0xFF0A1014),
    inputFieldBackground = Color(0xFF1A2228),
    dividerColor = Color(0xFF1A2228),
    secondaryText = WhatsAppTextSecondary,
    unreadBadge = WhatsAppAccent
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
    val BubbleCornerRadius: Dp = 18.dp
    @Deprecated("Beta design uses uniform corners, no tail")
    val BubbleTailRadius: Dp = 4.dp
    val BubblePaddingHorizontal: Dp = 8.dp
    val BubblePaddingVertical: Dp = 6.dp
    val BubbleMaxWidthFraction: Float = 0.65f
    val ImagePreviewMaxHeight: Dp = 200.dp
    val StickerSize: Dp = 150.dp

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

// ─── M3 Color schemes ──────────────────────────────────────

val MuhabbetLightColorScheme = lightColorScheme(
    primary = WhatsAppAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F1E5),
    onPrimaryContainer = Color(0xFF002117),
    secondary = WhatsAppUnreadLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Color(0xFFFFB300),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFECB3),
    onTertiaryContainer = Color(0xFF7F6003),
    error = Red700,
    onError = Color.White,
    surface = Color.White,
    onSurface = WhatsAppTextPrimaryLight,
    surfaceVariant = WhatsAppInputFieldLight,
    onSurfaceVariant = WhatsAppTextSecondaryLight,
    outline = WhatsAppDividerLight,
    outlineVariant = Color(0xFFCAC4D0)
)

val MuhabbetDarkColorScheme = darkColorScheme(
    primary = WhatsAppAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = WhatsAppAccent,
    onSecondary = Color(0xFF003A08),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFFFFB300),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFECB3),
    error = Red400,
    onError = Color(0xFF601410),
    surface = WhatsAppDarkSurface,
    onSurface = WhatsAppTextPrimary,
    surfaceVariant = WhatsAppDarkElevated,
    onSurfaceVariant = WhatsAppTextSecondary,
    outline = WhatsAppDividerDark,
    outlineVariant = Color(0xFF49454F),
    background = WhatsAppDarkBg,
    onBackground = WhatsAppTextPrimary
)

// OLED Black theme — true black backgrounds for AMOLED displays
val MuhabbetOledBlackColorScheme = darkColorScheme(
    primary = WhatsAppAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003D36),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = WhatsAppAccent,
    onSecondary = Color(0xFF003A08),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFFFFB300),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFECB3),
    error = Red400,
    onError = Color(0xFF601410),
    surface = Color(0xFF0A1014),
    onSurface = WhatsAppTextPrimary,
    surfaceVariant = Color(0xFF1A2228),
    onSurfaceVariant = WhatsAppTextSecondary,
    outline = Color(0xFF1A2228),
    outlineVariant = Color(0xFF49454F),
    background = Color.Black,
    onBackground = WhatsAppTextPrimary
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
    val semanticColors = when {
        isDark && oledBlack -> OledSemanticColors
        isDark -> DarkSemanticColors
        else -> LightSemanticColors
    }

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

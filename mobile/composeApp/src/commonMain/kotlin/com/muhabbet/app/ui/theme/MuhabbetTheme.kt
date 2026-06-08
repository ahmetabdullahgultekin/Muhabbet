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

// ─── Muhabbet identity palette — İznik çini (see docs/design/D1-brand-color-identity.md) ──
// Own identity, NOT a WhatsApp clone: firuze (turquoise) primary, kobalt (cobalt) ticks/links,
// mercan (coral) warm accent. Drawn from classical Turkish İznik tile art.

// Firuze (turquoise) — primary brand accent
private val CiniFiruze = Color(0xFF0E94A8)         // primary (light)
private val CiniFiruzeBright = Color(0xFF26B3C7)   // brighter accent (dark / online dot)
// Kobalt (cobalt blue) — read-ticks + links
private val CiniKobalt = Color(0xFF1E5AA8)         // deep cobalt (links, containers)
private val CiniReadTickLight = Color(0xFF3E8FD0)  // read-tick (light)
private val CiniReadTickDark = Color(0xFF5BB7D8)   // read-tick (dark)
// Mercan (coral red) — warm signature accent
private val CiniMercan = Color(0xFFE2553D)

// Dark surfaces — subtle teal family so they pair with firuze
private val CiniDarkBg = Color(0xFF101C20)
private val CiniDarkSurface = Color(0xFF18272C)
private val CiniDarkElevated = Color(0xFF223539)
private val CiniWallpaperDark = Color(0xFF0B1518)
private val CiniOwnBubbleDark = Color(0xFF0A5560)  // deep firuze own-bubble
private val CiniTextPrimaryDark = Color(0xFFE8EFF0)
private val CiniTextSecondaryDark = Color(0xFF8AA0A6)
private val CiniDividerDark = Color(0xFF223539)

// Light surfaces — warm ivory wallpaper + pale firuze own-bubble
private val CiniOwnBubbleLight = Color(0xFFCDEFF3)
private val CiniWallpaperLight = Color(0xFFEDE7DA)  // warm ivory (was WhatsApp beige)
private val CiniFiruzeLightAccent = Color(0xFF13A0B5) // online/unread (light)
private val CiniTextPrimaryLight = Color(0xFF0E2A2F)  // deep teal-ink
private val CiniTextSecondaryLight = Color(0xFF5C7178)
private val CiniInputFieldLight = Color(0xFFEFF2F1)
private val CiniDividerLight = Color(0xFFE4EAE8)

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
    statusOnline = CiniFiruzeLightAccent,
    statusRead = CiniReadTickLight,
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFFBDBDBD),
    callDecline = Color(0xFFE53935),
    callAccept = Color(0xFF43A047),
    callMissed = Color(0xFFE53935),
    bubbleOwn = CiniOwnBubbleLight,
    bubbleOther = Color.White,
    onBubbleOwn = CiniTextPrimaryLight,
    onBubbleOther = CiniTextPrimaryLight,
    linkColor = CiniKobalt,
    chatWallpaper = CiniWallpaperLight,
    inputBarBackground = Color.White,
    inputFieldBackground = CiniInputFieldLight,
    dividerColor = CiniDividerLight,
    secondaryText = CiniTextSecondaryLight,
    unreadBadge = CiniFiruzeLightAccent
)

val DarkSemanticColors = MuhabbetSemanticColors(
    statusOnline = CiniFiruzeBright,
    statusRead = CiniReadTickDark,
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFF757575),
    callDecline = Color(0xFFEF5350),
    callAccept = Color(0xFF43A047),
    callMissed = Color(0xFFEF5350),
    bubbleOwn = CiniOwnBubbleDark,
    bubbleOther = CiniDarkSurface,
    onBubbleOwn = CiniTextPrimaryDark,
    onBubbleOther = CiniTextPrimaryDark,
    linkColor = CiniFiruzeBright,
    chatWallpaper = CiniWallpaperDark,
    inputBarBackground = CiniDarkSurface,
    inputFieldBackground = CiniDarkElevated,
    dividerColor = CiniDividerDark,
    secondaryText = CiniTextSecondaryDark,
    unreadBadge = CiniFiruzeBright
)

val OledSemanticColors = MuhabbetSemanticColors(
    statusOnline = CiniFiruzeBright,
    statusRead = CiniReadTickDark,
    statusDelivered = Color(0xFF9E9E9E),
    statusSending = Color(0xFF757575),
    callDecline = Color(0xFFEF5350),
    callAccept = Color(0xFF43A047),
    callMissed = Color(0xFFEF5350),
    bubbleOwn = CiniOwnBubbleDark,
    bubbleOther = Color(0xFF08191C),
    onBubbleOwn = CiniTextPrimaryDark,
    onBubbleOther = CiniTextPrimaryDark,
    linkColor = CiniFiruzeBright,
    chatWallpaper = Color.Black,
    inputBarBackground = Color(0xFF08191C),
    inputFieldBackground = Color(0xFF15252A),
    dividerColor = Color(0xFF15252A),
    secondaryText = CiniTextSecondaryDark,
    unreadBadge = CiniFiruzeBright
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
    primary = CiniFiruze,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3E8EF),
    onPrimaryContainer = Color(0xFF00363E),
    secondary = CiniKobalt,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E3F7),
    onSecondaryContainer = Color(0xFF0A2A52),
    tertiary = CiniMercan,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF410001),
    error = Red700,
    onError = Color.White,
    surface = Color.White,
    onSurface = CiniTextPrimaryLight,
    surfaceVariant = CiniInputFieldLight,
    onSurfaceVariant = CiniTextSecondaryLight,
    outline = CiniDividerLight,
    outlineVariant = Color(0xFFCAC4D0)
)

val MuhabbetDarkColorScheme = darkColorScheme(
    primary = CiniFiruzeBright,
    onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF0A5560),
    onPrimaryContainer = Color(0xFFB3E8EF),
    secondary = Color(0xFF8FB8E8),
    onSecondary = Color(0xFF0A2A52),
    secondaryContainer = Color(0xFF1E5AA8),
    onSecondaryContainer = Color(0xFFD6E3F7),
    tertiary = Color(0xFFFFB4A2),
    onTertiary = Color(0xFF5F1500),
    tertiaryContainer = Color(0xFF8C3A28),
    onTertiaryContainer = Color(0xFFFFDAD2),
    error = Red400,
    onError = Color(0xFF601410),
    surface = CiniDarkSurface,
    onSurface = CiniTextPrimaryDark,
    surfaceVariant = CiniDarkElevated,
    onSurfaceVariant = CiniTextSecondaryDark,
    outline = CiniDividerDark,
    outlineVariant = Color(0xFF49454F),
    background = CiniDarkBg,
    onBackground = CiniTextPrimaryDark
)

// OLED Black theme — true black backgrounds for AMOLED displays
val MuhabbetOledBlackColorScheme = darkColorScheme(
    primary = CiniFiruzeBright,
    onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF06424B),
    onPrimaryContainer = Color(0xFFB3E8EF),
    secondary = Color(0xFF8FB8E8),
    onSecondary = Color(0xFF0A2A52),
    secondaryContainer = Color(0xFF1E5AA8),
    onSecondaryContainer = Color(0xFFD6E3F7),
    tertiary = Color(0xFFFFB4A2),
    onTertiary = Color(0xFF5F1500),
    tertiaryContainer = Color(0xFF8C3A28),
    onTertiaryContainer = Color(0xFFFFDAD2),
    error = Red400,
    onError = Color(0xFF601410),
    surface = Color(0xFF08191C),
    onSurface = CiniTextPrimaryDark,
    surfaceVariant = Color(0xFF15252A),
    onSurfaceVariant = CiniTextSecondaryDark,
    outline = Color(0xFF15252A),
    outlineVariant = Color(0xFF49454F),
    background = Color.Black,
    onBackground = CiniTextPrimaryDark
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

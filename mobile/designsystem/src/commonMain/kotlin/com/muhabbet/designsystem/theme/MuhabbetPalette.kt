package com.muhabbet.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Every raw colour in the app, and the three Material colour schemes built from them.
 *
 * `internal` throughout: outside this module the only things visible are [MuhabbetSemanticColors]
 * and the schemes below, so no screen can name a hex. That is the module boundary doing its job —
 * it used to be a convention, and the convention lost.
 *
 * These values are still the inherited WhatsApp palette, extracted here unchanged so that replacing
 * them with Muhabbet's own identity is a single-file diff, and so git history separates
 * "restructured the palette" from "changed the brand".
 */
internal object MuhabbetPalette {
    val Accent = Color(0xFF00A884)
    val DarkBg = Color(0xFF111B21)
    val DarkSurface = Color(0xFF1F2C34)
    val DarkElevated = Color(0xFF2A3942)
    val WallpaperDark = Color(0xFF0D1418)
    val OwnBubbleDark = Color(0xFF005C4B)
    val TextPrimary = Color(0xFFE9EDEF)
    val TextSecondary = Color(0xFF8696A0)
    val ReadTickDark = Color(0xFF53BDEB)
    val OwnBubbleLight = Color(0xFFD9FDD3)
    val WallpaperLight = Color(0xFFECE5DD)
    val UnreadLight = Color(0xFF25D366)
    val TextPrimaryLight = Color(0xFF111B21)
    val TextSecondaryLight = Color(0xFF667781)
    val ReadTickLight = Color(0xFF4FB6EC)
    val InputFieldLight = Color(0xFFF0F2F5)
    val DividerLight = Color(0xFFE9EDEF)
    val DividerDark = Color(0xFF2A3942)

    val Red700 = Color(0xFFD32F2F)
    val Red400 = Color(0xFFEF5350)
}

/*
 * A note on the surfaceContainer* roles below, because their absence was a live bug.
 *
 * Material 3 derives the container colour of NavigationBar, Card, DropdownMenu, ModalBottomSheet
 * and the default TopAppBar from surfaceContainer*. None of the three schemes set them, so M3 fell
 * back to its own tonal defaults — which is why CallHistoryScreen and UpdatesTabScreen (the two
 * bottom-nav tabs with no explicit top-bar colours) rendered a different bar from every other
 * screen, and why switching tabs changed the bar colour for no reason.
 *
 * Filling them in is therefore a visible change, and an intended one: several "inconsistencies"
 * disappear without anyone styling a screen.
 */

internal val MuhabbetLightColorScheme: ColorScheme = lightColorScheme(
    primary = MuhabbetPalette.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F1E5),
    onPrimaryContainer = Color(0xFF002117),
    secondary = MuhabbetPalette.UnreadLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Color(0xFFFFB300),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFECB3),
    onTertiaryContainer = Color(0xFF7F6003),
    error = MuhabbetPalette.Red700,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFDFDFD),
    onBackground = MuhabbetPalette.TextPrimaryLight,
    surface = Color.White,
    onSurface = MuhabbetPalette.TextPrimaryLight,
    surfaceVariant = MuhabbetPalette.InputFieldLight,
    onSurfaceVariant = MuhabbetPalette.TextSecondaryLight,
    surfaceTint = MuhabbetPalette.Accent,
    surfaceDim = Color(0xFFE3E7E9),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = MuhabbetPalette.InputFieldLight,
    surfaceContainerHigh = MuhabbetPalette.DividerLight,
    surfaceContainerHighest = Color(0xFFE1E6E9),
    inverseSurface = Color(0xFF2E3438),
    inverseOnSurface = Color(0xFFF0F2F4),
    inversePrimary = Color(0xFF6FDFC7),
    outline = MuhabbetPalette.DividerLight,
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color.Black
)

internal val MuhabbetDarkColorScheme: ColorScheme = darkColorScheme(
    primary = MuhabbetPalette.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = MuhabbetPalette.Accent,
    onSecondary = Color(0xFF003A08),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFFFFB300),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFECB3),
    error = MuhabbetPalette.Red400,
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = MuhabbetPalette.DarkBg,
    onBackground = MuhabbetPalette.TextPrimary,
    surface = MuhabbetPalette.DarkSurface,
    onSurface = MuhabbetPalette.TextPrimary,
    surfaceVariant = MuhabbetPalette.DarkElevated,
    onSurfaceVariant = MuhabbetPalette.TextSecondary,
    surfaceTint = MuhabbetPalette.Accent,
    surfaceDim = MuhabbetPalette.WallpaperDark,
    surfaceBright = Color(0xFF33444E),
    surfaceContainerLowest = MuhabbetPalette.WallpaperDark,
    surfaceContainerLow = MuhabbetPalette.DarkBg,
    surfaceContainer = MuhabbetPalette.DarkSurface,
    surfaceContainerHigh = MuhabbetPalette.DarkElevated,
    surfaceContainerHighest = Color(0xFF33444E),
    inverseSurface = MuhabbetPalette.TextPrimary,
    inverseOnSurface = MuhabbetPalette.DarkBg,
    inversePrimary = Color(0xFF00695C),
    outline = MuhabbetPalette.DividerDark,
    outlineVariant = Color(0xFF49454F),
    scrim = Color.Black
)

/** True-black variant for AMOLED panels, where an unlit pixel costs nothing. */
internal val MuhabbetOledBlackColorScheme: ColorScheme = darkColorScheme(
    primary = MuhabbetPalette.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003D36),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = MuhabbetPalette.Accent,
    onSecondary = Color(0xFF003A08),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFFFFB300),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFECB3),
    error = MuhabbetPalette.Red400,
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color.Black,
    onBackground = MuhabbetPalette.TextPrimary,
    surface = Color(0xFF0A1014),
    onSurface = MuhabbetPalette.TextPrimary,
    surfaceVariant = Color(0xFF1A2228),
    onSurfaceVariant = MuhabbetPalette.TextSecondary,
    surfaceTint = MuhabbetPalette.Accent,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF232C33),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A1014),
    surfaceContainer = Color(0xFF12181C),
    surfaceContainerHigh = Color(0xFF1A2228),
    surfaceContainerHighest = Color(0xFF232C33),
    inverseSurface = MuhabbetPalette.TextPrimary,
    inverseOnSurface = Color.Black,
    inversePrimary = Color(0xFF00695C),
    outline = Color(0xFF1A2228),
    outlineVariant = Color(0xFF49454F),
    scrim = Color.Black
)

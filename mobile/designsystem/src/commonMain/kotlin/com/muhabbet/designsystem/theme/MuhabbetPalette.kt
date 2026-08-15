package com.muhabbet.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Every raw colour in the app: a warm neutral ramp and a copper accent.
 *
 * `internal` throughout: outside this module the only things visible are [MuhabbetSemanticColors]
 * and the schemes below, so no screen can name a hex. That is the module boundary doing its job —
 * it used to be a convention, and the convention lost.
 *
 * **This replaces the inherited WhatsApp palette.** The variables in the previous version were
 * literally called `WhatsAppAccent = 0xFF00A884` and `WhatsAppOwnBubbleLight = 0xFFD9FDD3`; the
 * repo's own roadmap listed de-cloning the theme as a brand and legal risk before any screenshot
 * goes public. Nothing here is a recolour of those values — the hue, the neutral temperature and the
 * bubble treatment are all different.
 *
 * Two decisions worth keeping:
 *
 * **The neutrals are warm, not blue-grey.** Next to copper a blue-grey neutral reads as dirty. Every
 * surface, divider and secondary text colour comes off the [Ink] ramp, and the ramp is what
 * `surfaceContainer*` is derived from rather than each container being picked by hand — that is the
 * difference between a dark theme that reads as designed and one that reads as assembled.
 *
 * **Green survives only where it is semantic** — `statusOnline` and `callAccept` — and is shifted
 * off `#25D366`. Read ticks stay a cool blue: copper cannot hold 3:1 against a copper bubble, and
 * the warm-brand / cool-status split is useful chromatic separation in its own right.
 */
internal object MuhabbetPalette {

    /**
     * Warm ink. Fourteen steps, numbered by approximate lightness so that "one step darker" is a
     * thing you can say. `surfaceContainerLowest…Highest` walk this ramp directly.
     */
    object Ink {
        val I00 = Color(0xFF0B0A09)
        val I05 = Color(0xFF12100E)
        val I10 = Color(0xFF1C1917)
        val I15 = Color(0xFF262220)
        val I20 = Color(0xFF322D2A)
        val I30 = Color(0xFF453E3A)
        val I40 = Color(0xFF5C534E)
        val I50 = Color(0xFF7A6F68)
        val I60 = Color(0xFF9C8F86)
        val I70 = Color(0xFFBDB0A6)
        val I80 = Color(0xFFDAD1C9)
        val I90 = Color(0xFFEFE9E3)
        val I95 = Color(0xFFF7F3EF)
        val I99 = Color(0xFFFDFBF9)
    }

    /** The accent. Light themes take the darker half, dark themes the lighter half. */
    object Copper {
        val C30 = Color(0xFF6B3B10)
        val C40 = Color(0xFF8A4E17)
        val C50 = Color(0xFFA85F1C)
        val C60 = Color(0xFFC9752C)
        val C70 = Color(0xFFE08A3C)
        val C80 = Color(0xFFF0A868)
        val C90 = Color(0xFFF8CFA6)
    }

    /** Off-white for text on dark surfaces — warm, so it does not read blue against the ink. */
    val PaperOnDark = Color(0xFFF5F1EC)

    /** Own-message bubble. A pale copper wash in light, a deep copper-brown in dark. */
    val BubbleOwnLight = Color(0xFFFBE7D2)
    val BubbleOwnDark = Color(0xFF4A3016)

    /** Chat wallpaper: a warm tint a step off the surface, so bubbles have something to sit on. */
    val WallpaperLight = Color(0xFFF3EDE7)

    /**
     * The middle stop of the full-screen brand gradient (auth, calls).
     *
     * Deliberately barely there. The gradient rule allows a full-bleed ground because it is an
     * identity moment, but a ground carrying a form has to stay out of the way of the text on top
     * of it — so the travel from one end to the other is a few percent of luminance, not a wash.
     */
    val BackdropTintLight = Color(0xFFF6E9DA)
    val BackdropTintDark = Color(0xFF1F1611)

    // Semantic non-brand hues. Green only where it means "connected" or "answer".
    val Success = Color(0xFF1F7A4D)
    val SuccessOnDark = Color(0xFF4BAE7F)
    val Danger = Color(0xFFB3261E)
    val DangerOnDark = Color(0xFFF2837C)

    /** Read receipts. Cool on purpose — see the class docblock. */
    val InfoBlue = Color(0xFF1F6FA8)
    val InfoBlueOnDark = Color(0xFF7FC4EE)

    // Container tones for the M3 roles, derived from the two ramps above.
    val CopperContainerLight = Color(0xFFF3DFC9)
    val OnCopperContainerLight = Color(0xFF3A1D00)
    val OnSecondaryContainerLight = Color(0xFF33200A)
    val InfoContainerLight = Color(0xFFCFE5F5)
    val OnInfoContainerLight = Color(0xFF0A2B45)
    val InfoContainerDark = Color(0xFF16496B)
    val CopperContainerDark = Color(0xFF5C3410)
    val DangerContainerLight = Color(0xFFFFDAD6)
    val OnDangerContainerLight = Color(0xFF410002)
    val DangerContainerDark = Color(0xFF8C1D18)
    val OnDangerDark = Color(0xFF52130F)
    val SurfaceHighestLight = Color(0xFFE7E0D9)
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
 * They now walk the Ink ramp, one step per level.
 */

internal val MuhabbetLightColorScheme: ColorScheme = lightColorScheme(
    primary = MuhabbetPalette.Copper.C50,
    onPrimary = Color.White,
    primaryContainer = MuhabbetPalette.Copper.C90,
    onPrimaryContainer = MuhabbetPalette.OnCopperContainerLight,
    secondary = MuhabbetPalette.Copper.C40,
    onSecondary = Color.White,
    secondaryContainer = MuhabbetPalette.CopperContainerLight,
    onSecondaryContainer = MuhabbetPalette.OnSecondaryContainerLight,
    tertiary = MuhabbetPalette.InfoBlue,
    onTertiary = Color.White,
    tertiaryContainer = MuhabbetPalette.InfoContainerLight,
    onTertiaryContainer = MuhabbetPalette.OnInfoContainerLight,
    error = MuhabbetPalette.Danger,
    onError = Color.White,
    errorContainer = MuhabbetPalette.DangerContainerLight,
    onErrorContainer = MuhabbetPalette.OnDangerContainerLight,
    background = MuhabbetPalette.Ink.I99,
    onBackground = MuhabbetPalette.Ink.I10,
    surface = Color.White,
    onSurface = MuhabbetPalette.Ink.I10,
    surfaceVariant = MuhabbetPalette.Ink.I95,
    onSurfaceVariant = MuhabbetPalette.Ink.I40,
    surfaceTint = MuhabbetPalette.Copper.C50,
    surfaceDim = MuhabbetPalette.Ink.I90,
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = MuhabbetPalette.Ink.I99,
    surfaceContainer = MuhabbetPalette.Ink.I95,
    surfaceContainerHigh = MuhabbetPalette.Ink.I90,
    surfaceContainerHighest = MuhabbetPalette.SurfaceHighestLight,
    inverseSurface = MuhabbetPalette.Ink.I20,
    inverseOnSurface = MuhabbetPalette.Ink.I95,
    inversePrimary = MuhabbetPalette.Copper.C80,
    outline = MuhabbetPalette.Ink.I60,
    outlineVariant = MuhabbetPalette.Ink.I80,
    scrim = Color.Black
)

internal val MuhabbetDarkColorScheme: ColorScheme = darkColorScheme(
    primary = MuhabbetPalette.Copper.C70,
    onPrimary = MuhabbetPalette.Ink.I05,
    primaryContainer = MuhabbetPalette.CopperContainerDark,
    onPrimaryContainer = MuhabbetPalette.Copper.C90,
    secondary = MuhabbetPalette.Copper.C60,
    onSecondary = MuhabbetPalette.Ink.I05,
    secondaryContainer = MuhabbetPalette.BubbleOwnDark,
    onSecondaryContainer = MuhabbetPalette.Copper.C90,
    tertiary = MuhabbetPalette.InfoBlueOnDark,
    onTertiary = MuhabbetPalette.OnInfoContainerLight,
    tertiaryContainer = MuhabbetPalette.InfoContainerDark,
    onTertiaryContainer = MuhabbetPalette.InfoContainerLight,
    error = MuhabbetPalette.DangerOnDark,
    onError = MuhabbetPalette.OnDangerDark,
    errorContainer = MuhabbetPalette.DangerContainerDark,
    onErrorContainer = Color(0xFFF9DEDC),
    background = MuhabbetPalette.Ink.I05,
    onBackground = MuhabbetPalette.PaperOnDark,
    surface = MuhabbetPalette.Ink.I10,
    onSurface = MuhabbetPalette.PaperOnDark,
    // I15 rather than I20: onSurfaceVariant is I60, and against I20 that lands at 4.33:1 — under
    // the 4.5 body-text floor. One step darker and the same secondary-text colour passes at 5.02.
    surfaceVariant = MuhabbetPalette.Ink.I15,
    onSurfaceVariant = MuhabbetPalette.Ink.I60,
    surfaceTint = MuhabbetPalette.Copper.C70,
    surfaceDim = MuhabbetPalette.Ink.I00,
    surfaceBright = MuhabbetPalette.Ink.I30,
    surfaceContainerLowest = MuhabbetPalette.Ink.I00,
    surfaceContainerLow = MuhabbetPalette.Ink.I05,
    surfaceContainer = MuhabbetPalette.Ink.I10,
    surfaceContainerHigh = MuhabbetPalette.Ink.I15,
    surfaceContainerHighest = MuhabbetPalette.Ink.I20,
    inverseSurface = MuhabbetPalette.Ink.I90,
    inverseOnSurface = MuhabbetPalette.Ink.I10,
    inversePrimary = MuhabbetPalette.Copper.C40,
    // I50 rather than I40: an outline carries information, so it needs 3:1 against the surface it
    // sits on. I40 on I10 is 2.33:1.
    outline = MuhabbetPalette.Ink.I50,
    outlineVariant = MuhabbetPalette.Ink.I20,
    scrim = Color.Black
)

/** True-black variant for AMOLED panels, where an unlit pixel costs nothing. */
internal val MuhabbetOledBlackColorScheme: ColorScheme = darkColorScheme(
    primary = MuhabbetPalette.Copper.C70,
    onPrimary = MuhabbetPalette.Ink.I05,
    primaryContainer = MuhabbetPalette.CopperContainerDark,
    onPrimaryContainer = MuhabbetPalette.Copper.C90,
    secondary = MuhabbetPalette.Copper.C60,
    onSecondary = MuhabbetPalette.Ink.I05,
    secondaryContainer = MuhabbetPalette.BubbleOwnDark,
    onSecondaryContainer = MuhabbetPalette.Copper.C90,
    tertiary = MuhabbetPalette.InfoBlueOnDark,
    onTertiary = MuhabbetPalette.OnInfoContainerLight,
    tertiaryContainer = MuhabbetPalette.InfoContainerDark,
    onTertiaryContainer = MuhabbetPalette.InfoContainerLight,
    error = MuhabbetPalette.DangerOnDark,
    onError = MuhabbetPalette.OnDangerDark,
    errorContainer = MuhabbetPalette.DangerContainerDark,
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color.Black,
    onBackground = MuhabbetPalette.PaperOnDark,
    surface = MuhabbetPalette.Ink.I00,
    onSurface = MuhabbetPalette.PaperOnDark,
    surfaceVariant = MuhabbetPalette.Ink.I10,
    onSurfaceVariant = MuhabbetPalette.Ink.I60,
    surfaceTint = MuhabbetPalette.Copper.C70,
    surfaceDim = Color.Black,
    surfaceBright = MuhabbetPalette.Ink.I20,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = MuhabbetPalette.Ink.I00,
    surfaceContainer = MuhabbetPalette.Ink.I05,
    surfaceContainerHigh = MuhabbetPalette.Ink.I10,
    surfaceContainerHighest = MuhabbetPalette.Ink.I15,
    inverseSurface = MuhabbetPalette.Ink.I90,
    inverseOnSurface = MuhabbetPalette.Ink.I00,
    inversePrimary = MuhabbetPalette.Copper.C40,
    outline = MuhabbetPalette.Ink.I50,
    outlineVariant = MuhabbetPalette.Ink.I10,
    scrim = Color.Black
)

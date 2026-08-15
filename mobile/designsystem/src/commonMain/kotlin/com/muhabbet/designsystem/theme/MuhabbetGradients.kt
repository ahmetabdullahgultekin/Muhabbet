package com.muhabbet.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The gradients the app is allowed to use, and nothing else.
 *
 * The rule, from the design system doc: **a gradient either covers ≥25% of the viewport, or it is
 * decoration carrying no text. Never behind body copy.** Message bubbles and CTAs are explicitly
 * excluded — a gradient bubble is 2014 skeuomorphism and wrecks text contrast, and a gradient CTA is
 * a crypto-app tell. Keeping the permitted set in one object is what makes that rule checkable
 * rather than aspirational.
 */
object MuhabbetGradients {

    /**
     * Full-screen identity ground for auth and call screens.
     *
     * Three stops rather than two so the middle can carry a hint of copper without either end
     * drifting off the surface colour. The luminance travel is a few percent: this sits under a
     * form, and the contrast test measures text against a flat surface colour, so a ground that
     * actually moved would invalidate those measurements.
     */
    val brandBackdrop: Brush
        @Composable @ReadOnlyComposable get() {
            val stops = when (LocalThemeMode.current) {
                ResolvedThemeMode.Light -> listOf(
                    MuhabbetPalette.Ink.I99,
                    MuhabbetPalette.BackdropTintLight,
                    MuhabbetPalette.Ink.I95
                )

                ResolvedThemeMode.Dark -> listOf(
                    MuhabbetPalette.Ink.I05,
                    MuhabbetPalette.BackdropTintDark,
                    MuhabbetPalette.Ink.I00
                )

                // True black at both ends: on an OLED panel the unlit pixels are the point, and a
                // lifted corner would be the one place the black is not black.
                ResolvedThemeMode.Oled -> listOf(
                    Color.Black,
                    MuhabbetPalette.BackdropTintDark,
                    Color.Black
                )
            }
            return Brush.verticalGradient(stops)
        }

    /**
     * The ring around the brand mark. A sweep so the copper travels around the circle rather than
     * across it — the one place in the app where the accent is allowed to be decorative.
     */
    val brandRing: Brush
        @Composable @ReadOnlyComposable get() = Brush.sweepGradient(
            listOf(
                MuhabbetPalette.Copper.C40,
                MuhabbetPalette.Copper.C70,
                MuhabbetPalette.Copper.C90,
                MuhabbetPalette.Copper.C60,
                MuhabbetPalette.Copper.C40
            )
        )

    /**
     * Deterministic two-stop fill for an avatar with no photo, seeded from the display name.
     *
     * The cheapest high-impact change available: it turns a contact list of identical grey circles
     * into something that looks art-directed, and because the seed is the name, the same person is
     * the same colour on every device and across restarts.
     *
     * Hues are picked off the copper ramp and its warm neighbours only — a random-hue rainbow would
     * fight the palette, and the point is that the list reads as one system.
     */
    fun avatarFallback(seed: String): Brush {
        val pairs = AvatarFallbackPairs
        // Sum of code points rather than String.hashCode(): hashCode's contract does not guarantee
        // stability across platforms, and this colour has to match on Android and iOS.
        val index = (seed.sumOf { it.code } % pairs.size).let { if (it < 0) it + pairs.size else it }
        val (top, bottom) = pairs[index]
        return Brush.linearGradient(listOf(top, bottom))
    }
}

/**
 * Chat wallpapers the user can pick from.
 *
 * The previous set was a 2010s navy-and-purple selection (`#1A1A2E`, `#533483`, `#0F3460`) that
 * predates the palette and fights it — a cool violet behind copper bubbles reads as two apps stacked.
 * These are drawn from the Ink and Copper ramps plus a few muted neighbours, so any of them can sit
 * behind a bubble without arguing with it.
 *
 * Exposed as a list rather than named tokens because the user picks one by eye; the app never refers
 * to "the third wallpaper" in code.
 */
val MuhabbetWallpapers: List<Color> = listOf(
    // Light, warm — the default family.
    MuhabbetPalette.WallpaperLight,
    MuhabbetPalette.Ink.I95,
    MuhabbetPalette.Ink.I90,
    Color(0xFFF6E9DA),
    Color(0xFFEFE3D2),
    Color(0xFFE9DCCB),
    // Deep, for dark and OLED.
    MuhabbetPalette.Ink.I10,
    MuhabbetPalette.Ink.I05,
    MuhabbetPalette.Ink.I00,
    Color(0xFF241A12),
    Color(0xFF2A1E14),
    Color(0xFF1A1512)
)

/**
 * Six warm pairs. Enough that a screenful of contacts does not obviously repeat, few enough that
 * they read as a set.
 */
private val AvatarFallbackPairs: List<Pair<Color, Color>> = listOf(
    MuhabbetPalette.Copper.C50 to MuhabbetPalette.Copper.C70,
    MuhabbetPalette.Copper.C30 to MuhabbetPalette.Copper.C50,
    Color(0xFF8A5A2B) to Color(0xFFC08A4E),
    Color(0xFF7A4A3A) to Color(0xFFB57A5E),
    Color(0xFF6B5238) to Color(0xFFA8875C),
    Color(0xFF8A4438) to Color(0xFFC47A62)
)

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
 *
 * ### Why there are more than twelve of them now (#380)
 *
 * The first version of this list was twelve swatches in **one hue family** — six warm near-white
 * beiges and six near-black inks — and the owner's report of it was "the options are very limited,
 * is there no colour palette?". Six creams that differ by two or three percent luminance do not read
 * as a curated set; at swatch size they read as the same colour printed six times, which is why the
 * screen looked unfinished rather than restrained.
 *
 * The rule the wider set still keeps is the one that motivated the narrow set in the first place: a
 * wallpaper sits behind copper bubbles, so **chroma stays low**. These are hues, not saturated
 * colours — the green is a sage, the blue is a harbour grey, the violet is a mauve. That is what
 * lets the list widen from one family to seven without any of them arguing with a bubble.
 *
 * Nothing was removed. A swatch dropped from this list would still be painted by any chat that had
 * stored its hex, but the picker would stop marking it as chosen — a settings screen showing
 * "nothing selected" while the chat shows a colour is the same dishonesty this whole feature was
 * fixed for.
 */
val MuhabbetWallpapers: List<Color> = listOf(
    // ── Light. The warm family first — it is the brand's own ground and the default.
    MuhabbetPalette.WallpaperLight,
    MuhabbetPalette.Ink.I95,
    MuhabbetPalette.Ink.I90,
    Color(0xFFF6E9DA),
    Color(0xFFEFE3D2),
    Color(0xFFE9DCCB),
    // …then the other six families, at the same luminance so the tier still reads as one set.
    Color(0xFFF1E2DD), // clay
    Color(0xFFECE9D6), // wheat
    Color(0xFFE2E9DE), // sage
    Color(0xFFDDE8E7), // sea
    Color(0xFFDFE4EE), // harbour
    Color(0xFFE9E0EA), // mauve
    // ── Deep, for dark and OLED. Same seven families, same order.
    MuhabbetPalette.Ink.I10,
    MuhabbetPalette.Ink.I05,
    MuhabbetPalette.Ink.I00,
    Color(0xFF241A12),
    Color(0xFF2A1E14),
    Color(0xFF1A1512),
    Color(0xFF221614), // clay
    Color(0xFF1E1F14), // wheat
    Color(0xFF16201A), // sage
    Color(0xFF122020), // sea
    Color(0xFF141A26), // harbour
    Color(0xFF1E1622)  // mauve
)

/**
 * A gradient a user can pick as their chat wallpaper: a stable [id] that survives a restart, and the
 * stops to paint it with.
 *
 * The **id** is what gets persisted, never the colours. Storing the stops would freeze a chat on
 * whatever the palette happened to be the day the user tapped the swatch, and a palette revision
 * would then have to reach into every user's preferences to correct it.
 */
data class MuhabbetWallpaperGradient(
    val id: String,
    val stops: List<Color>
) {
    /** Vertical, because a chat scrolls vertically and a diagonal wash fights that. */
    val brush: Brush get() = Brush.verticalGradient(stops)
}

/**
 * The gradient wallpapers, in the same two tiers as [MuhabbetWallpapers].
 *
 * [MuhabbetGradients] above states the rule these have to satisfy: a gradient either covers ≥25% of
 * the viewport, or it is decoration carrying no text — never behind body copy. A chat wallpaper covers
 * the whole viewport, so the first half holds by construction. The second half does not: message text
 * is body copy and it sits over this. So the travel is bounded rather than free.
 *
 * The bound is not a guess. Only one text surface on a chat is translucent enough to let the wallpaper
 * through at all — message bubbles are fully opaque since #678 took the alpha off the deleted one,
 * and the date-separator pill is `surfaceVariant`
 * at [MuhabbetAlphas.ChatOverlaySurface] (`DateSeparator.kt`), so a tenth of the wallpaper reaches the
 * label behind it. Both stops of all eight gradients were measured through that bleed, in all three
 * themes: the worst case is **5.48:1** for the pill label, against a WCAG AA floor of 4.5:1. The travel
 * that budget buys is 6–13 points of CIE L*, which is enough to read as a gradient rather than as
 * another flat swatch — the whole reason this list exists next to [MuhabbetWallpapers].
 *
 * Re-measure before widening a stop. A gradient dramatic enough to notice would put the pill label on a
 * different background at the top of the screen than at the bottom, which is exactly the point at which
 * one measured contrast ratio stops describing the screen.
 */
val MuhabbetWallpaperGradients: List<MuhabbetWallpaperGradient> = listOf(
    // Light.
    MuhabbetWallpaperGradient("dawn", listOf(Color(0xFFF9F2E8), Color(0xFFEBDCC6))),
    MuhabbetWallpaperGradient("clay", listOf(Color(0xFFF6EAE5), Color(0xFFE6D2CB))),
    MuhabbetWallpaperGradient("sage", listOf(Color(0xFFEBF1E6), Color(0xFFD6E1D0))),
    MuhabbetWallpaperGradient("harbour", listOf(Color(0xFFE8EDF4), Color(0xFFD2DBE9))),
    // Deep.
    MuhabbetWallpaperGradient("ember", listOf(Color(0xFF2E2117), Color(0xFF0E0B09))),
    MuhabbetWallpaperGradient("forest", listOf(Color(0xFF1D2A22), Color(0xFF0A110D))),
    MuhabbetWallpaperGradient("midnight", listOf(Color(0xFF1E2839), Color(0xFF090C12))),
    MuhabbetWallpaperGradient("plum", listOf(Color(0xFF2A1E2F), Color(0xFF110D14)))
)

/**
 * The gradient stored under [id], or null if nothing in the current set answers to it.
 *
 * Null is a real answer here, not a failure: a preference written by a later build — or by one whose
 * set has since been revised — names an id this build cannot paint. Callers fall back to the theme's
 * own wallpaper, which is exactly what a SOLID selection with an unparseable hex already does.
 */
fun muhabbetWallpaperGradient(id: String): MuhabbetWallpaperGradient? =
    MuhabbetWallpaperGradients.firstOrNull { it.id == id }

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

package com.muhabbet.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.fail

/**
 * The immersive story/media viewer's backdrop and the text drawn on it, measured in all three
 * themes and over both extremes of a photograph.
 *
 * Sibling of [DeletedBubbleContrastTest] and [WallpaperContrastTest], written for the same class of
 * defect and expressing the same kind of rule: not "this hex is correct" but "whatever the palette
 * says, this pairing clears the floor."
 *
 * ### What it caught (#586)
 *
 * `StatusViewerScreen` painted its backdrop with `colorScheme.scrim` and every glyph on it with
 * `colorScheme.inverseOnSurface`. Those are not a pair and were never measured as one.
 * `inverseOnSurface` is the content colour for **`inverseSurface`**, which in a dark scheme is a
 * *light* plate — so its foreground is a near-black ink. Drawn on `scrim`, which is `Color.Black`
 * in every scheme:
 *
 * | theme | foreground | ratio |
 * |---|---|---|
 * | Light | `inverseOnSurface` = Ink.I95 | 19.02:1 — fine, which is why this survived review |
 * | Dark | `inverseOnSurface` = Ink.I10 | **1.20:1** |
 * | Oled | `inverseOnSurface` = Ink.I00 | **1.06:1** |
 *
 * The owner's report was not "hard to read". At 1.06:1 the text is the background.
 *
 * The fix is that the screen takes [MuhabbetSemanticColors.scrim] — a ground that arrives with the
 * one foreground measured on it — and this file measures both halves of that pair rather than the
 * screen's use of it. Two properties are asserted rather than one hex pinned: the pair clears AA,
 * and the pair is identical in all three themes, which is what makes a theme-independent viewer
 * theme-independent.
 *
 * ### The half a palette cannot answer
 *
 * A story is text over **arbitrary user media**. No token can promise contrast against a
 * photograph, so the viewer gives its text a ground of its own — the scrim container at
 * [MuhabbetAlphas.MediaScrim]. The photo tests below composite that plate over pure white and pure
 * black: a photograph can be any colour but cannot be brighter than white or darker than black, so
 * clearing at both ends clears for every picture in between.
 *
 * What that does **not** cover is whether the plate is actually under the glyphs on a real device —
 * that is layout, not arithmetic, and it needs a screen.
 *
 * Pure arithmetic on `Color`, and the luminance maths is its own copy rather than a call into
 * [readableContentOn] — a test that reuses the implementation it checks proves only that the
 * implementation agrees with itself.
 */
class StatusScrimContrastTest {

    /** WCAG 2.1 SC 1.4.3, normal-size body text. Every foreground on this screen is prose. */
    private val textFloor = 4.5

    /** WCAG 2.1 SC 1.4.11, a graphical object that carries information. */
    private val graphicFloor = 3.0

    /** WCAG relative luminance: sRGB channel, linearised, then weighted. */
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * Straight sRGB compositing, which is what Compose draws for a translucent colour over an
     * opaque ground. Not a perceptual blend — the point is to reproduce the pixel.
     */
    private fun over(foreground: Color, background: Color, alpha: Float): Color = Color(
        red = foreground.red * alpha + background.red * (1 - alpha),
        green = foreground.green * alpha + background.green * (1 - alpha),
        blue = foreground.blue * alpha + background.blue * (1 - alpha)
    )

    private fun themes(): List<Triple<String, MuhabbetSemanticColors, ColorScheme>> = listOf(
        Triple("Light", LightSemanticColors, MuhabbetLightColorScheme),
        Triple("Dark", DarkSemanticColors, MuhabbetDarkColorScheme),
        Triple("Oled", OledSemanticColors, MuhabbetOledBlackColorScheme)
    )

    /**
     * The two ends of every photograph. Anything a camera can produce sits between them.
     */
    private fun photoExtremes(): List<Pair<String, Color>> =
        listOf("photo(white)" to Color.White, "photo(black)" to Color.Black)

    private fun Double.asRatio(): String {
        val rounded = (this * 100).toInt() / 100.0
        return "$rounded:1"
    }

    @Test
    fun viewerText_onItsOwnBackdrop_meetsAA_inEveryTheme() {
        val failures = themes().mapNotNull { (name, colors, _) ->
            val ratio = contrast(colors.scrim.content, colors.scrim.container)
            if (ratio < textFloor) "  $name: ${ratio.asRatio()} (needs $textFloor:1)" else null
        }
        if (failures.isNotEmpty()) {
            fail(
                "The immersive viewer's own foreground is unreadable on its own backdrop in " +
                    "${failures.size} theme(s):\n" + failures.joinToString("\n")
            )
        }
    }

    /**
     * States the mechanism, so a failure names the cause rather than a ratio.
     *
     * The viewer is theme-independent on purpose: a photograph is judged against black, never
     * against whatever the app's surface happens to be. That is only true while the pair itself is
     * theme-independent — the moment a scheme gives `scrim` a foreground of its own, the OLED
     * variant is free to drift back towards its background and #586 returns.
     */
    @Test
    fun scrimPair_isTheSameInEveryTheme_soTheViewerIsThemeIndependent() {
        val distinctContainers = themes().map { it.second.scrim.container }.distinct()
        val distinctContents = themes().map { it.second.scrim.content }.distinct()
        if (distinctContainers.size != 1 || distinctContents.size != 1) {
            fail(
                "The scrim pair now differs between themes (containers=$distinctContainers, " +
                    "contents=$distinctContents). The full-screen media and status viewers are " +
                    "deliberately theme-independent; per-theme scrim foregrounds are how the OLED " +
                    "scheme ended up drawing Ink.I00 on Color.Black (#586)."
            )
        }
    }

    /**
     * `inverseOnSurface` measured against the surface it is actually the foreground for.
     *
     * Half a regression guard and half an explanation: the token is not broken, it was simply
     * being used with a ground it was never paired with. Keeping this here means the next person
     * who reaches for it on the viewer can see, in one file, both that it is fine where it belongs
     * and what it did where it did not.
     */
    @Test
    fun inverseOnSurface_clearsAA_againstInverseSurface_whichIsItsActualPartner() {
        val failures = themes().mapNotNull { (name, _, scheme) ->
            val ratio = contrast(scheme.inverseOnSurface, scheme.inverseSurface)
            if (ratio < textFloor) "  $name: ${ratio.asRatio()} (needs $textFloor:1)" else null
        }
        if (failures.isNotEmpty()) {
            fail("inverseOnSurface fails on inverseSurface:\n" + failures.joinToString("\n"))
        }
    }

    /**
     * The plate the viewer puts between a photograph and its own text, measured at both ends of
     * what a photograph can be.
     *
     * This is the property [MuhabbetAlphas.MediaScrim]'s value was derived from, recomputed rather
     * than trusted: lower the alpha and this fails on the white end long before anyone opens the
     * app on a snow photo.
     */
    @Test
    fun viewerText_overAnyPhotograph_meetsAA_throughTheMediaScrim() {
        val failures = themes().flatMap { (themeName, colors, _) ->
            photoExtremes().mapNotNull { (photoName, photo) ->
                val plate = over(colors.scrim.container, photo, MuhabbetAlphas.MediaScrim)
                val ratio = contrast(colors.scrim.content, plate)
                if (ratio < textFloor) {
                    "  $themeName / $photoName: ${ratio.asRatio()} (needs $textFloor:1)"
                } else {
                    null
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(
                "Status text unreadable over ${failures.size} photo/theme combination(s) — the " +
                    "media scrim is too weak:\n" + failures.joinToString("\n")
            )
        }
    }

    /**
     * The segmented progress bar still says how many statuses there are, and how far through this
     * one you are, once a photograph is behind it.
     *
     * The information is carried by *filled versus unfilled*, so that is the pairing measured — a
     * graphical object at 3:1, not text at 4.5:1. The track keeps an alpha because it is a track;
     * the fill does not, because it is the part that has to be seen.
     */
    @Test
    fun progressBar_fillStandsOffItsTrack_overAnyPhotograph() {
        val failures = themes().flatMap { (themeName, colors, _) ->
            photoExtremes().mapNotNull { (photoName, photo) ->
                val plate = over(colors.scrim.container, photo, MuhabbetAlphas.MediaScrim)
                val track = over(colors.scrim.content, plate, MuhabbetAlphas.ProgressTrack)
                val ratio = contrast(colors.scrim.content, track)
                if (ratio < graphicFloor) {
                    "  $themeName / $photoName: ${ratio.asRatio()} (needs $graphicFloor:1)"
                } else {
                    null
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(
                "Story progress segments indistinguishable in ${failures.size} case(s):\n" +
                    failures.joinToString("\n")
            )
        }
    }

    /**
     * Guards the luminance maths itself, so a palette failure is never mistaken for a broken test.
     * Black on white is the WCAG reference maximum of exactly 21:1.
     */
    @Test
    fun contrastMaths_returns21_forBlackOnWhite() {
        val ratio = contrast(Color.Black, Color.White)
        if (kotlin.math.abs(ratio - 21.0) > 0.01) fail("Expected 21:1 for black on white, got $ratio")
    }
}

package com.muhabbet.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The chat wallpaper is the one ground in the app the palette does not choose — the user does.
 *
 * [SemanticColorContrastTest] checks the colours the theme declares against each other. It cannot
 * check this, because the background here is a swatch a user tapped, a gradient they picked, or a
 * photograph out of their camera roll, and the pairing only exists at runtime. So this file measures
 * the same WCAG floors across the whole space of grounds a chat can actually end up with.
 *
 * What is measured is the **date-separator pill** (`DateSeparator.kt`), because it is the only text
 * surface a chat draws straight onto the wallpaper — message bubbles are fully opaque, so nothing
 * behind them reaches their text. The pill is deliberately translucent, at
 * [MuhabbetAlphas.ChatOverlaySurface], which means `(1 - alpha)` of whatever the wallpaper is showing
 * blends into the ground its label is read against.
 *
 * The cross-tier cases are the point, and they are reachable rather than theoretical: the picker
 * offers every swatch in every theme, and the "dark mode wallpaper" toggle deliberately carries a
 * light selection into a dark chat. At the 80% the pill was hardcoded to before #380, a light
 * wallpaper in the dark theme measured 4.03:1 and a pure-white photo measured 3.88:1.
 *
 * Pure arithmetic on `Color`, and the luminance maths is its own copy rather than a call into
 * [readableContentOn] — a test that reuses the implementation it checks proves only that the
 * implementation agrees with itself.
 */
class WallpaperContrastTest {

    /** WCAG 2.1 1.4.3, normal-size body text. The pill's label is prose, so it takes the text floor. */
    private val textFloor = 4.5

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
     * Straight sRGB compositing, which is what `Color.copy(alpha = …)` over an opaque ground does
     * when Compose draws it. Not a perceptual blend — the point is to reproduce the pixel.
     */
    private fun over(foreground: Color, background: Color, alpha: Float): Color = Color(
        red = foreground.red * alpha + background.red * (1 - alpha),
        green = foreground.green * alpha + background.green * (1 - alpha),
        blue = foreground.blue * alpha + background.blue * (1 - alpha)
    )

    /**
     * Every ground a chat can paint behind the pill.
     *
     * The two photo extremes stand in for the whole of user media: a photograph can be any colour,
     * but it cannot be brighter than white or darker than black, so if the label clears at both ends
     * it clears for every picture in between.
     */
    private fun allWallpaperGrounds(): List<kotlin.Pair<String, Color>> =
        MuhabbetWallpapers.mapIndexed { index, color -> "solid[$index]" to color } +
            MuhabbetWallpaperGradients.flatMap { gradient ->
                gradient.stops.mapIndexed { index, color -> "gradient ${gradient.id}[$index]" to color }
            } +
            listOf("photo(white)" to Color.White, "photo(black)" to Color.Black)

    private fun assertPillReadable(themeName: String, scheme: ColorScheme) {
        val failures = allWallpaperGrounds().mapNotNull { (label, ground) ->
            val pill = over(scheme.surfaceVariant, ground, MuhabbetAlphas.ChatOverlaySurface)
            val ratio = contrast(scheme.onSurfaceVariant, pill)
            if (ratio < textFloor) "$themeName / $label: ${ratio.asRatio()} (needs $textFloor:1)" else null
        }
        if (failures.isNotEmpty()) {
            fail("Date-separator label unreadable over ${failures.size} wallpaper(s):\n" + failures.joinToString("\n"))
        }
    }

    private fun Double.asRatio(): String {
        val rounded = (this * 100).toInt() / 100.0
        return "$rounded:1"
    }

    @Test
    fun dateSeparatorLabel_overEveryWallpaper_inLightTheme_meetsAA() =
        assertPillReadable("Light", MuhabbetLightColorScheme)

    @Test
    fun dateSeparatorLabel_overEveryWallpaper_inDarkTheme_meetsAA() =
        assertPillReadable("Dark", MuhabbetDarkColorScheme)

    @Test
    fun dateSeparatorLabel_overEveryWallpaper_inOledTheme_meetsAA() =
        assertPillReadable("Oled", MuhabbetOledBlackColorScheme)

    /**
     * The stored preference is a gradient **id**, so two gradients sharing one would make the pick
     * ambiguous and `muhabbetWallpaperGradient` would silently resolve to whichever came first.
     */
    @Test
    fun gradientIds_areUnique() {
        val ids = MuhabbetWallpaperGradients.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Duplicate gradient id in MuhabbetWallpaperGradients: $ids")
    }

    /**
     * Every shipped gradient must be findable by the id that gets persisted, and an id that is not
     * shipped must resolve to null rather than to something arbitrary — the chat falls back to the
     * theme's own wallpaper on null, which is the documented contract for a preference written by a
     * build whose set has since changed.
     */
    @Test
    fun gradientLookup_findsEveryShippedIdAndNothingElse() {
        MuhabbetWallpaperGradients.forEach { gradient ->
            assertEquals(gradient, muhabbetWallpaperGradient(gradient.id), "Lookup failed for '${gradient.id}'")
        }
        assertTrue(muhabbetWallpaperGradient("not-a-shipped-gradient") == null)
    }

    /**
     * The design system's stated budget for a chat wallpaper gradient: **4 to 13 points of CIE L***
     * between its extreme stops (docs/design/muhabbet-design-system.md, §5 Gradients).
     *
     * The floor matters as much as the ceiling. Under about 4 points the "gradient" is
     * indistinguishable from a flat swatch at picker size, which is the whole reason this set exists
     * alongside [MuhabbetWallpapers]; over 13 the date pill sits on a measurably different ground at
     * the top of the screen than at the bottom, and one contrast number stops describing it.
     */
    @Test
    fun everyGradient_staysWithinTheDocumentedLuminanceBudget() {
        fun lStar(c: Color): Double {
            val y = luminance(c)
            return if (y > 0.008856) 116.0 * y.pow(1.0 / 3.0) - 16.0 else 903.3 * y
        }
        MuhabbetWallpaperGradients.forEach { gradient ->
            val lightnesses = gradient.stops.map(::lStar)
            val travel = (lightnesses.max() - lightnesses.min())
            assertTrue(
                travel in 4.0..13.0,
                "Gradient '${gradient.id}' travels ${(travel * 10).toInt() / 10.0} points of L*, " +
                    "outside the documented 4–13 budget"
            )
        }
    }

    /**
     * A one-stop "gradient" is a solid pretending to be one, and an empty list crashes
     * `Brush.verticalGradient`. Both are the kind of thing a hurried palette edit produces.
     */
    @Test
    fun everyGradient_hasAtLeastTwoStops() {
        MuhabbetWallpaperGradients.forEach { gradient ->
            assertTrue(gradient.stops.size >= 2, "Gradient '${gradient.id}' has ${gradient.stops.size} stop(s)")
        }
    }
}

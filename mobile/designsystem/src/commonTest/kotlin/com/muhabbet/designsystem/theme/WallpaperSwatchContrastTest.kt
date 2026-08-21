package com.muhabbet.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.fail

/**
 * The other half of [WallpaperContrastTest]: **is the swatch itself visible, in its own picker?**
 *
 * Those are two different contrast relationships and only one of them was ever measured.
 * [WallpaperContrastTest] measures *text on the wallpaper* — the date-separator pill's label, over
 * every ground a chat can end up with, against WCAG's 4.5:1 text floor. It did that thoroughly, and
 * it says nothing at all about the grid of squares the user picks from, where the relationship is
 * *wallpaper sample on the app's own page*.
 *
 * The unmeasured half was broken. On the OLED theme the deepest ink swatch on a black page is
 * **1.06:1** — a square the user cannot see and can still tap (#697). And it was never only OLED:
 * in the light theme all twelve pale swatches sit between 1.07:1 and 1.31:1 against the near-white
 * page. Half the palette was invisible in every theme; the report only named OLED because that is
 * the theme it was found in.
 *
 * The floor here is **3:1** — WCAG 2.1 SC 1.4.11, user-interface components and graphical objects —
 * not the 4.5:1 the sibling file uses. A swatch is an object, not prose, and holding it to a text
 * floor would be inventing a requirement rather than checking one.
 *
 * What makes a swatch perceivable is either its own fill or the outline [swatchOutlineOn] derives
 * for it; this asserts that at least one of the two clears the floor for every swatch, every theme
 * and both of the grounds a picker can sit on. The luminance maths below is its own copy rather than
 * a call into the design system's, for the reason the sibling file gives: a test that reuses the
 * implementation it checks proves only that the implementation agrees with itself. The outline
 * colour, by contrast, comes from the real [swatchOutlineOn] — that function is the thing under
 * test.
 *
 * The one thing this cannot see is a picker that stops drawing the outline. `WallpaperSwatch` in
 * `WallpaperPickerScreen` is the single call site, and its docblock says why the outline is not
 * decoration.
 */
class WallpaperSwatchContrastTest {

    /** WCAG 2.1 SC 1.4.11. A swatch is a graphical object, so this is its floor, not 4.5:1. */
    private val objectFloor = 3.0

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

    private fun Double.asRatio(): String = "${(this * 100).toInt() / 100.0}:1"

    /**
     * One drawn area of one swatch, and the colour the picker derives its outline and tick from.
     *
     * A gradient contributes both its stops as separate areas, because the outline runs the whole
     * way around and one derived colour has to hold at both ends. The picker derives from
     * `stops.last()`; passing that same source here is deliberate — it is what ships.
     */
    private data class SwatchArea(val label: String, val fill: Color, val derivedFrom: Color)

    private fun pickerSwatches(): List<SwatchArea> =
        MuhabbetWallpapers.mapIndexed { index, color ->
            SwatchArea("solid[$index]", color, color)
        } + MuhabbetWallpaperGradients.flatMap { gradient ->
            gradient.stops.mapIndexed { index, stop ->
                SwatchArea("gradient ${gradient.id}[$index]", stop, gradient.stops.last())
            }
        }

    /**
     * Both grounds a swatch grid can be drawn on.
     *
     * `background` is the live one — `MuhabbetScaffold` wraps Material's `Scaffold`, whose container
     * colour is `colorScheme.background`. `surface` is measured too so that moving the grid into a
     * card or a sheet later cannot silently reintroduce the defect.
     */
    private fun grounds(scheme: ColorScheme): List<Pair<String, Color>> =
        listOf("background" to scheme.background, "surface" to scheme.surface)

    private fun assertSwatchesPerceivable(themeName: String, scheme: ColorScheme) {
        val failures = pickerSwatches().flatMap { swatch ->
            grounds(scheme).mapNotNull { (groundName, ground) ->
                val fill = contrast(swatch.fill, ground)
                val edge = contrast(swatchOutlineOn(swatch.derivedFrom), ground)
                val best = maxOf(fill, edge)
                if (best < objectFloor) {
                    "$themeName / ${swatch.label} on $groundName: fill ${fill.asRatio()}, " +
                        "outline ${edge.asRatio()} (needs $objectFloor:1)"
                } else {
                    null
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} wallpaper swatch(es) invisible on their own picker:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun everySwatch_isPerceivableInItsPicker_inLightTheme() =
        assertSwatchesPerceivable("Light", MuhabbetLightColorScheme)

    @Test
    fun everySwatch_isPerceivableInItsPicker_inDarkTheme() =
        assertSwatchesPerceivable("Dark", MuhabbetDarkColorScheme)

    @Test
    fun everySwatch_isPerceivableInItsPicker_inOledTheme() =
        assertSwatchesPerceivable("Oled", MuhabbetOledBlackColorScheme)

    /**
     * The outline has to stand away from the swatch it rings as well as from the page behind it.
     *
     * An outline that merges into its own fill draws no boundary — the swatch would still be a
     * shapeless area, just an area with a slightly different edge. This is theme-independent: both
     * colours come from the swatch, not from the scheme.
     *
     * It covers the selection tick at the same time, because the tick is drawn in the same colour
     * and sits on the same fill. That is the half of #697 that would otherwise be missed: marking a
     * square nobody can see with a tick nobody can see is not a selected state.
     */
    @Test
    fun everyOutlineAndTick_standsAwayFromItsOwnSwatch() {
        val failures = pickerSwatches().mapNotNull { swatch ->
            val ratio = contrast(swatchOutlineOn(swatch.derivedFrom), swatch.fill)
            if (ratio < objectFloor) {
                "${swatch.label}: outline/tick ${ratio.asRatio()} on its own fill (needs $objectFloor:1)"
            } else {
                null
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} swatch(es) whose outline and tick vanish into them:\n" + failures.joinToString("\n"))
        }
    }

    /**
     * The selected swatch is ringed in `primary`, which is the one mark here the swatch does not
     * derive — so it is checked against the page rather than against the fill.
     *
     * Against the page is the right pairing for a boundary: the ring separates the swatch from the
     * screen around it, and a mark that clears 3:1 on one of the two surfaces it lies between is
     * visible. The other surface is covered by the previous test, which holds the tick inside the
     * ring above the same floor on every fill.
     */
    @Test
    fun selectionRing_isPerceivableOnEveryPage() {
        val themes = listOf(
            "Light" to MuhabbetLightColorScheme,
            "Dark" to MuhabbetDarkColorScheme,
            "Oled" to MuhabbetOledBlackColorScheme
        )
        val failures = themes.flatMap { (themeName, scheme) ->
            grounds(scheme).mapNotNull { (groundName, ground) ->
                val ratio = contrast(scheme.primary, ground)
                if (ratio < objectFloor) {
                    "$themeName / $groundName: selection ring ${ratio.asRatio()} (needs $objectFloor:1)"
                } else {
                    null
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail("Selection ring invisible on ${failures.size} page(s):\n" + failures.joinToString("\n"))
        }
    }
}

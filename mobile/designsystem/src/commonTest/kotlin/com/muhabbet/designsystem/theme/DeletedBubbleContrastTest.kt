package com.muhabbet.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.fail

/**
 * The deleted-message bubble, measured over every ground a chat can put behind it.
 *
 * Sibling of [WallpaperContrastTest], which measures the date-separator pill for the same reason and
 * used to be able to say that it was the *only* text surface a chat drew straight onto the wallpaper
 * — "message bubbles are fully opaque, so nothing behind them reaches their text". That was true of
 * every bubble but one. A deleted message was drawn as `surfaceVariant` at **50%** alpha with the
 * label at another 50% on top of it, so half the wallpaper reached the ground its own label was read
 * against, and the label's own translucency halved what was left (#678).
 *
 * What that measured, before this fix:
 *
 * | theme | ground | label |
 * |---|---|---|
 * | Light | the app's own default wallpaper | **2.23:1** |
 * | Light | near-black swatch | 1.36:1 |
 * | Dark | light swatch (reachable — the dark-mode wallpaper toggle carries a light pick into a dark chat) | 1.28:1 |
 * | Dark | a white photo | **1.23:1** |
 *
 * The default-wallpaper row is the one worth keeping in mind: this was never only a
 * custom-wallpaper defect, and the issue that reported it measured the *timestamp* (which is drawn
 * in the opaque bubble's content colour at full alpha, and did clear on the default). Two texts on
 * that bubble were wrong in two different ways.
 *
 * The floor here is **4.5:1**, WCAG 2.1 SC 1.4.3 for normal-size body text — not the 3:1 of SC
 * 1.4.11, which is what `WallpaperSwatchContrastTest` uses for a swatch as a graphical object. Both
 * things this measures are prose: the tombstone label and the bubble's timestamp. The circle-slash
 * glyph beside the label *is* a graphical object at 3:1, and it is drawn in the same colour as the
 * label, so the text floor covers it with room to spare.
 *
 * Pure arithmetic on `Color`, and the luminance maths is its own copy rather than a call into
 * [readableContentOn] — a test that reuses the implementation it checks proves only that the
 * implementation agrees with itself.
 */
class DeletedBubbleContrastTest {

    /** WCAG 2.1 SC 1.4.3, normal-size body text. */
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
     * Straight sRGB compositing, which is what Compose draws for a translucent colour over an opaque
     * ground. Not a perceptual blend — the point is to reproduce the pixel.
     */
    private fun over(foreground: Color, background: Color, alpha: Float): Color = Color(
        red = foreground.red * alpha + background.red * (1 - alpha),
        green = foreground.green * alpha + background.green * (1 - alpha),
        blue = foreground.blue * alpha + background.blue * (1 - alpha)
    )

    /**
     * Every ground a chat can paint behind a bubble: all 24 solid swatches, both stops of all 8
     * gradients, and the two photo extremes.
     *
     * The photo pair stands in for the whole of user media — a photograph can be any colour, but it
     * cannot be brighter than white or darker than black, so clearing at both ends clears for every
     * picture in between.
     */
    private fun allWallpaperGrounds(): List<kotlin.Pair<String, Color>> =
        MuhabbetWallpapers.mapIndexed { index, color -> "solid[$index]" to color } +
            MuhabbetWallpaperGradients.flatMap { gradient ->
                gradient.stops.mapIndexed { index, color -> "gradient ${gradient.id}[$index]" to color }
            } +
            listOf("photo(white)" to Color.White, "photo(black)" to Color.Black)

    /**
     * Composites the bubble onto a wallpaper **at whatever alpha the token declares**, then measures
     * each foreground on the result.
     *
     * Reading `container.alpha` rather than assuming 1f is deliberate: at the opacity shipped today
     * the wallpaper cancels out and all 42 grounds give the same number, which is the property being
     * asserted rather than an assumption being made. Put the alpha back and this test starts
     * reporting the wallpaper-dependent ratios that #678 was.
     */
    private fun assertTombstoneReadable(themeName: String, colors: MuhabbetSemanticColors, scheme: ColorScheme) {
        val pair = colors.bubbleDeleted
        val failures = allWallpaperGrounds().flatMap { (groundName, wallpaper) ->
            val bubble = over(pair.container, wallpaper, pair.container.alpha)
            listOf(
                // The tombstone label and the timestamp beside it. One colour, because a deleted
                // bubble carries no alpha anywhere — see MessageBubble.metaColor.
                "label + timestamp" to pair.content,
                // A group message keeps its sender name, which is drawn from the scheme rather than
                // from the pair, so it has to be named here or nothing measures it.
                "sender name" to scheme.primary
            ).mapNotNull { (what, foreground) ->
                val ratio = contrast(over(foreground, bubble, foreground.alpha), bubble)
                if (ratio < textFloor) {
                    "  $themeName / $groundName / $what: ${ratio.asRatio()} (needs $textFloor:1)"
                } else {
                    null
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(
                "Deleted-message bubble unreadable over ${failures.size} wallpaper/foreground " +
                    "combination(s):\n" + failures.joinToString("\n")
            )
        }
    }

    private fun Double.asRatio(): String {
        val rounded = (this * 100).toInt() / 100.0
        return "$rounded:1"
    }

    @Test
    fun deletedMessage_overEveryWallpaper_inLightTheme_meetsAA() =
        assertTombstoneReadable("Light", LightSemanticColors, MuhabbetLightColorScheme)

    @Test
    fun deletedMessage_overEveryWallpaper_inDarkTheme_meetsAA() =
        assertTombstoneReadable("Dark", DarkSemanticColors, MuhabbetDarkColorScheme)

    @Test
    fun deletedMessage_overEveryWallpaper_inOledTheme_meetsAA() =
        assertTombstoneReadable("Oled", OledSemanticColors, MuhabbetOledBlackColorScheme)

    /**
     * States the mechanism directly, so the failure message names the cause rather than a ratio.
     *
     * The test above would catch a returning alpha through its consequences, but only on the extreme
     * grounds, and its output would be forty lines of numbers. This one says what is wrong.
     */
    @Test
    fun deletedBubble_carriesNoAlpha_soTheWallpaperCannotReachItsText() {
        listOf(
            "Light" to LightSemanticColors,
            "Dark" to DarkSemanticColors,
            "Oled" to OledSemanticColors
        ).forEach { (name, colors) ->
            val pair = colors.bubbleDeleted
            if (pair.container.alpha != 1f || pair.content.alpha != 1f) {
                fail(
                    "$name bubbleDeleted is translucent (container ${pair.container.alpha}, " +
                        "content ${pair.content.alpha}). A bubble is drawn on the chat wallpaper, " +
                        "which the user chooses and the palette cannot measure — mute a deleted " +
                        "message with its colours, its italic label and its glyph, never with alpha " +
                        "(#678)."
                )
            }
        }
    }

    /**
     * The ground has to be its own, or "deleted" is carried by the italic and the glyph alone.
     *
     * Not a contrast floor — a design assertion, and the cheapest possible statement of it. Setting
     * this pair equal to `bubbleOther` would leave every measurement in this file passing while the
     * treatment quietly stopped existing.
     */
    @Test
    fun deletedBubble_isNotTheGroundALiveMessageUses() {
        listOf(
            "Light" to LightSemanticColors,
            "Dark" to DarkSemanticColors,
            "Oled" to OledSemanticColors
        ).forEach { (name, colors) ->
            assertNotEquals(colors.bubbleOwn.container, colors.bubbleDeleted.container, "$name: deleted == own bubble")
            assertNotEquals(colors.bubbleOther.container, colors.bubbleDeleted.container, "$name: deleted == other bubble")
        }
    }

    /**
     * WCAG 1.4.1: colour may not be the only thing carrying a distinction.
     *
     * The treatment this replaced satisfied that with translucency, which is exactly what cost the
     * label its contrast. The italic costs nothing and survives greyscale, a monochrome display and
     * every form of colour blindness. `MuhabbetIcons.MessageDeleted` beside it is the second
     * channel; that one cannot be asserted here, because an `ImageVector` identity says nothing
     * about whether a screen draws it.
     */
    @Test
    fun deletedLabel_isItalic_soDeletionSurvivesGreyscale() {
        val styles = MuhabbetTextStyles(MuhabbetTypeScale)
        if (styles.ChatDeletedLabel.fontStyle != FontStyle.Italic) {
            fail(
                "ChatDeletedLabel is no longer italic. Deletion then reads only as a colour, and " +
                    "the colour is a muted ink two rungs off the body text — put a second channel " +
                    "back before removing this one (#678)."
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

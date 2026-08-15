package com.muhabbet.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.fail

/**
 * WCAG contrast floors for every foreground/background pair the semantic palette declares.
 *
 * This is the merge gate for a palette change. `MuhabbetSemanticColors` has 25 fields and three
 * instances, so re-branding moves 75 colours at once; a single pair falling under the floor is
 * invisible in review, invisible on a bright desk, and obvious to a user at 40% brightness outdoors.
 *
 * Pure arithmetic on `Color` — no Android framework, no device, no Docker. It runs wherever the
 * common tests run, which is the point: it is the one part of the visual system that can be checked
 * automatically before anything is drawn.
 */
class SemanticColorContrastTest {

    /**
     * WCAG 2.1 minimums.
     *
     * Text is 4.5:1 (1.4.3, normal-size body text). Non-text is 3.0:1 (1.4.11): delivery ticks,
     * presence dots and badges are small glyphs carrying meaning, not prose, and holding them to the
     * text floor would force a palette nobody wants.
     */
    private val textFloor = 4.5
    private val nonTextFloor = 3.0

    private data class Pair(
        val label: String,
        val foreground: Color,
        val background: Color,
        val floor: Double
    )

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
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Flattens a translucent foreground onto its background before measuring.
     *
     * Several roles are used at reduced alpha (secondary text, disabled ticks). Measuring the
     * declared colour rather than the composited one reports a contrast the user never sees.
     */
    private fun over(foreground: Color, background: Color): Color {
        val a = foreground.alpha
        if (a >= 1f) return foreground
        return Color(
            red = foreground.red * a + background.red * (1 - a),
            green = foreground.green * a + background.green * (1 - a),
            blue = foreground.blue * a + background.blue * (1 - a)
        )
    }

    private fun pairsFor(name: String, c: MuhabbetSemanticColors): List<Pair> = listOf(
        // Message bubbles — the highest-traffic text in the product.
        Pair("$name onBubbleOwn/bubbleOwn", c.onBubbleOwn, c.bubbleOwn, textFloor),
        Pair("$name onBubbleOther/bubbleOther", c.onBubbleOther, c.bubbleOther, textFloor),
        // Bubble metadata sits at 11sp, the smallest type in the app, so it gets the text floor too.
        Pair("$name secondaryText/bubbleOther", c.secondaryText, c.bubbleOther, textFloor),
        Pair("$name secondaryText/inputBarBackground", c.secondaryText, c.inputBarBackground, textFloor),
        // Unread badge carries a number.
        Pair("$name onUnreadBadge/unreadBadge", c.onUnreadBadge, c.unreadBadge, textFloor),
        // Call actions carry labels on a coloured button.
        Pair("$name onCallAccept/callAccept", c.onCallAccept, c.callAccept, textFloor),
        Pair("$name onCallDecline/callDecline", c.onCallDecline, c.callDecline, textFloor),
        // The media viewer is theme-independent, but its chrome still has to be readable.
        Pair("$name onScrim/scrim", c.onScrim, c.scrim, textFloor),
        Pair("$name onScrim/scrimOverlay", c.onScrim, over(c.scrimOverlay, c.scrim), textFloor),
        // Links are text.
        Pair("$name linkColor/bubbleOther", c.linkColor, c.bubbleOther, textFloor),
        Pair("$name linkColor/bubbleOwn", c.linkColor, c.bubbleOwn, textFloor),

        // Non-text: small meaningful glyphs.
        Pair("$name statusRead/bubbleOwn", c.statusRead, c.bubbleOwn, nonTextFloor),
        Pair("$name statusDelivered/bubbleOwn", c.statusDelivered, c.bubbleOwn, nonTextFloor),
        Pair("$name statusSending/bubbleOwn", c.statusSending, c.bubbleOwn, nonTextFloor),
        Pair("$name statusOnline/inputBarBackground", c.statusOnline, c.inputBarBackground, nonTextFloor),
        Pair("$name callMissed/inputBarBackground", c.callMissed, c.inputBarBackground, nonTextFloor)
    )

    /**
     * Pairs that already fail today, in the WhatsApp-clone palette this project inherited.
     *
     * These are pre-existing accessibility defects, not new ones — the unread badge is white on
     * #25D366 at 1.98:1, and the "sending" clock sits on the own-bubble green at 1.69:1. They are
     * recorded rather than ignored so the test can be a TWO-WAY ratchet: a pair outside this set
     * dropping below its floor fails the build, and a pair inside it rising above its floor also
     * fails, with a message telling you to delete the entry.
     *
     * The brand palette replacing these colours must empty this set. A test that is simply expected
     * to be red gets ignored and then deleted; one that pins the exact known debt does not.
     */
    /**
     * Empty, and it stays empty.
     *
     * This held 21 pairs inherited with the cloned palette — an unread badge at 1.98:1, a "sending"
     * clock at 1.69:1 on its own bubble. Every one of them is now above its floor under the copper
     * palette. The test fails in both directions, so a pair that regresses fails the build and a
     * pair that is fixed has to be removed from here; adding an entry back is a deliberate act with
     * a reviewer attached.
     */
    private val knownDebt = emptySet<String>()

    private fun assertAllPairs(name: String, colors: MuhabbetSemanticColors) {
        val regressions = mutableListOf<String>()
        val fixed = mutableListOf<String>()

        pairsFor(name, colors).forEach { pair ->
            val ratio = contrast(over(pair.foreground, pair.background), pair.background)
            val isKnown = pair.label in knownDebt
            if (ratio < pair.floor && !isKnown) {
                regressions += "  ${pair.label}: ${format(ratio)}:1 (needs ${pair.floor}:1)"
            } else if (ratio >= pair.floor && isKnown) {
                fixed += "  ${pair.label}: now ${format(ratio)}:1 — remove it from knownDebt"
            }
        }

        if (regressions.isNotEmpty() || fixed.isNotEmpty()) {
            fail(
                buildString {
                    if (regressions.isNotEmpty()) {
                        appendLine("$name introduced ${regressions.size} new WCAG contrast failure(s):")
                        appendLine(regressions.joinToString("\n"))
                    }
                    if (fixed.isNotEmpty()) {
                        appendLine("$name fixed ${fixed.size} known failure(s) — tighten the ratchet:")
                        appendLine(fixed.joinToString("\n"))
                    }
                }
            )
        }
    }

    private fun format(value: Double): String {
        val scaled = kotlin.math.round(value * 100) / 100.0
        return scaled.toString()
    }

    @Test
    fun should_meet_wcag_contrast_when_light_theme() =
        assertAllPairs("Light", LightSemanticColors)

    @Test
    fun should_meet_wcag_contrast_when_dark_theme() =
        assertAllPairs("Dark", DarkSemanticColors)

    @Test
    fun should_meet_wcag_contrast_when_oled_theme() =
        assertAllPairs("Oled", OledSemanticColors)

    /**
     * Guards the luminance maths itself, so a palette failure is never mistaken for a broken test.
     * Black on white is the WCAG reference maximum of exactly 21:1.
     */
    @Test
    fun should_compute_known_ratio_when_black_on_white() {
        val ratio = contrast(Color.Black, Color.White)
        if (abs(ratio - 21.0) > 0.01) fail("Expected 21:1 for black on white, got $ratio")
    }
}

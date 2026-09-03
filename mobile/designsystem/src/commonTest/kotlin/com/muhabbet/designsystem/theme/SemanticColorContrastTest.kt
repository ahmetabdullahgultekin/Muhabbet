package com.muhabbet.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.fail

/**
 * WCAG contrast floors for every foreground/background pair the semantic palette declares.
 *
 * This is the merge gate for a palette change. Between the semantic tokens and the three Material
 * schemes a re-brand moves a couple of hundred colours at once; a single pair falling under the
 * floor is invisible in review, invisible on a bright desk, and obvious to a user at 40% brightness
 * outdoors.
 *
 * Three things are measured, and the third is the one #517 needed:
 *  1. every [MuhabbetColorPair] the theme declares, as the pair it claims to be;
 *  2. every mark — a tick, a link, a timestamp — against each ground it is actually drawn on;
 *  3. every Material role pairing, because filled selected states reach past the semantic tokens
 *     into `colorScheme` and nothing used to check what they found there.
 *
 * The luminance maths below is deliberately its own copy rather than a call into
 * [readableContentOn]. A test that reuses the implementation it is checking proves only that the
 * implementation agrees with itself.
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

    /**
     * The one deliberate exemption, and it is written down rather than left as a gap.
     *
     * WCAG 1.4.11 exempts purely decorative graphics, and M3's `outlineVariant` is exactly that: the
     * hairline between two list rows, where the separation is already carried by whitespace and the
     * line is a whisper on purpose. Held to 3:1 it would have to become a rule you notice, which is
     * not the design.
     *
     * It is still asserted, at a floor that only catches "the divider has become literally
     * invisible" — if a palette change drops one of these to 1.0 the test says so. Nothing else in
     * this file may use this floor; anything a user has to read or act on takes one of the two
     * above.
     */
    private val decorativeFloor = 1.1

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

    /** Every [MuhabbetColorPair] the theme declares, measured as the pair it claims to be. */
    private fun declaredPairs(name: String, c: MuhabbetSemanticColors): List<Pair> = listOf(
        "bubbleOwn" to c.bubbleOwn,
        "bubbleOther" to c.bubbleOther,
        "bubbleOwnInset" to c.bubbleOwnInset,
        "bubbleOwnInsetSelected" to c.bubbleOwnInsetSelected,
        "bubbleOtherInset" to c.bubbleOtherInset,
        "bubbleOtherInsetSelected" to c.bubbleOtherInsetSelected,
        // Measured here as the pair it claims to be, and again in DeletedBubbleContrastTest over
        // every wallpaper a chat can sit on — this bubble is drawn on the one ground the palette
        // does not choose, so the declared pairing is only half of what has to hold (#678).
        "bubbleDeleted" to c.bubbleDeleted,
        "unreadBadge" to c.unreadBadge,
        "callAccept" to c.callAccept,
        "callDecline" to c.callDecline,
        "chatWallpaper" to c.chatWallpaper,
        "inputBar" to c.inputBar,
        "inputField" to c.inputField,
        "selected" to c.selected,
        "selectedSubtle" to c.selectedSubtle,
        "scrim" to c.scrim
    ).map { (label, pair) ->
        Pair("$name $label.content/$label.container", pair.content, pair.container, textFloor)
    }

    private fun pairsFor(name: String, c: MuhabbetSemanticColors): List<Pair> = declaredPairs(name, c) + listOf(
        // The overlay is translucent, so it is measured composited onto the scrim beneath it.
        Pair("$name scrimOverlay.content/scrimOverlay.container", c.scrimOverlay.content, over(c.scrimOverlay.container, c.scrim.container), textFloor),

        // Marks have no partner of their own — what they must clear is whatever ground they land on,
        // so the grounds are named here rather than guessed at.
        // Bubble metadata sits at 11sp, the smallest type in the app, so it gets the text floor too.
        Pair("$name secondaryText/bubbleOther", c.secondaryText, c.bubbleOther.container, textFloor),
        Pair("$name secondaryText/bubbleOwn", c.secondaryText, c.bubbleOwn.container, textFloor),
        Pair("$name secondaryText/inputBar", c.secondaryText, c.inputBar.container, textFloor),
        Pair("$name secondaryText/inputField", c.secondaryText, c.inputField.container, textFloor),
        // Links are text.
        Pair("$name linkColor/bubbleOther", c.linkColor, c.bubbleOther.container, textFloor),
        Pair("$name linkColor/bubbleOwn", c.linkColor, c.bubbleOwn.container, textFloor),

        // Non-text: small meaningful glyphs.
        Pair("$name statusRead/bubbleOwn", c.statusRead, c.bubbleOwn.container, nonTextFloor),
        Pair("$name statusDelivered/bubbleOwn", c.statusDelivered, c.bubbleOwn.container, nonTextFloor),
        Pair("$name statusSending/bubbleOwn", c.statusSending, c.bubbleOwn.container, nonTextFloor),
        Pair("$name statusFailed/bubbleOwn", c.statusFailed, c.bubbleOwn.container, nonTextFloor),
        Pair("$name statusOnline/inputBar", c.statusOnline, c.inputBar.container, nonTextFloor),
        Pair("$name callMissed/inputBar", c.callMissed, c.inputBar.container, nonTextFloor),
        // A divider is a rule, not prose, and it is the one place the palette is deliberately quiet.
        Pair("$name dividerColor/bubbleOther", c.dividerColor, c.bubbleOther.container, decorativeFloor)
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

    // ─── Material 3 scheme roles ────────────────────────────────

    /**
     * Every `on-` role against the container it names, in all three schemes.
     *
     * The semantic pairs above are only half the palette. A selected chip, a filled tab, a radio row
     * — all of them reach past [MuhabbetSemanticColors] into `MaterialTheme.colorScheme`, and nothing
     * checked those. #517 was exactly that: a poll option drawn on `primaryContainer` with
     * `onSurfaceVariant` on top.
     */
    private fun schemePairsFor(name: String, s: ColorScheme): List<Pair> = listOf(
        Pair("$name onPrimary/primary", s.onPrimary, s.primary, textFloor),
        Pair("$name onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer, textFloor),
        Pair("$name onSecondary/secondary", s.onSecondary, s.secondary, textFloor),
        Pair("$name onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer, textFloor),
        Pair("$name onTertiary/tertiary", s.onTertiary, s.tertiary, textFloor),
        Pair("$name onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, textFloor),
        Pair("$name onError/error", s.onError, s.error, textFloor),
        Pair("$name onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer, textFloor),
        Pair("$name onBackground/background", s.onBackground, s.background, textFloor),
        Pair("$name onSurface/surface", s.onSurface, s.surface, textFloor),
        Pair("$name onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant, textFloor),
        Pair("$name inverseOnSurface/inverseSurface", s.inverseOnSurface, s.inverseSurface, textFloor),

        // M3 gives the surfaceContainer* levels no `on-` role of their own; onSurface is the partner,
        // and cards, sheets, menus and the nav bar all draw body text on them.
        Pair("$name onSurface/surfaceContainerLowest", s.onSurface, s.surfaceContainerLowest, textFloor),
        Pair("$name onSurface/surfaceContainerLow", s.onSurface, s.surfaceContainerLow, textFloor),
        Pair("$name onSurface/surfaceContainer", s.onSurface, s.surfaceContainer, textFloor),
        Pair("$name onSurface/surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh, textFloor),
        Pair("$name onSurface/surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest, textFloor),
        Pair("$name onSurfaceVariant/surfaceContainer", s.onSurfaceVariant, s.surfaceContainer, textFloor),
        Pair("$name onSurfaceVariant/surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh, textFloor),
        Pair("$name onSurfaceVariant/surfaceContainerHighest", s.onSurfaceVariant, s.surfaceContainerHighest, textFloor),

        // Accent text and accent glyphs on the plain surfaces. Section headers, links in settings,
        // the "unread" count, destructive row labels — all of them are prose in a brand colour.
        Pair("$name primary/surface", s.primary, s.surface, textFloor),
        Pair("$name primary/surfaceContainer", s.primary, s.surfaceContainer, textFloor),
        Pair("$name error/surface", s.error, s.surface, textFloor),
        Pair("$name secondary/surface", s.secondary, s.surface, textFloor),

        // Non-text: an outline carries information — a text field's border, a card's edge.
        Pair("$name outline/surface", s.outline, s.surface, nonTextFloor),
        Pair("$name outline/surfaceContainer", s.outline, s.surfaceContainer, nonTextFloor),
        Pair("$name outline/surfaceContainerHigh", s.outline, s.surfaceContainerHigh, nonTextFloor),
        // Decorative. See [decorativeFloor] — this is an exemption, on the record, not an omission.
        Pair("$name outlineVariant/surface", s.outlineVariant, s.surface, decorativeFloor),
        Pair("$name outlineVariant/surfaceContainer", s.outlineVariant, s.surfaceContainer, decorativeFloor)
    )

    private fun assertAllSchemePairs(name: String, scheme: ColorScheme) {
        val regressions = schemePairsFor(name, scheme)
            .filter { contrast(over(it.foreground, it.background), it.background) < it.floor }
            .map {
                val ratio = contrast(over(it.foreground, it.background), it.background)
                "  ${it.label}: ${format(ratio)}:1 (needs ${it.floor}:1)"
            }
        if (regressions.isNotEmpty()) {
            fail("$name scheme has ${regressions.size} WCAG contrast failure(s):\n${regressions.joinToString("\n")}")
        }
    }

    @Test
    fun should_meet_wcag_contrast_when_light_scheme_roles() =
        assertAllSchemePairs("LightScheme", MuhabbetLightColorScheme)

    @Test
    fun should_meet_wcag_contrast_when_dark_scheme_roles() =
        assertAllSchemePairs("DarkScheme", MuhabbetDarkColorScheme)

    @Test
    fun should_meet_wcag_contrast_when_oled_scheme_roles() =
        assertAllSchemePairs("OledScheme", MuhabbetOledBlackColorScheme)

    /**
     * The twelve wallpaper swatches, each against the foreground [readableContentOn] picks for it.
     *
     * The swatches are the one ground the palette does not choose — the user does — and they run
     * from near-white to near-black, so no single hardcoded tick colour can work on all of them.
     * A hardcoded white one is what shipped, and it was invisible on the six pale swatches. This is
     * the check that the rule replacing it actually holds across the whole set.
     *
     * The selection tick is a UI component, not prose, so the 3:1 floor applies. In practice every
     * swatch clears the text floor as well.
     */
    @Test
    fun should_meet_wcag_contrast_when_content_is_derived_for_a_wallpaper_swatch() {
        val failures = MuhabbetWallpapers.mapIndexedNotNull { index, swatch ->
            val pair = readableContentOn(swatch)
            val ratio = contrast(pair.content, pair.container)
            if (ratio < nonTextFloor) "  wallpaper[$index]: ${format(ratio)}:1 (needs $nonTextFloor:1)" else null
        }
        if (failures.isNotEmpty()) {
            fail("readableContentOn picked an illegible foreground for ${failures.size} swatch(es):\n${failures.joinToString("\n")}")
        }
    }

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

package com.muhabbet.designsystem.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The avatar initial corpus (#839). Runs against the real platform segmenter on every target —
 * `java.text.BreakIterator` in the Android unit tests, Foundation's composed-character ranges in
 * `iosSimulatorArm64Test` — so a platform that segments differently fails here, not in a list row.
 *
 * Every case below was wrong in the hand-rolled implementation this replaced: the flag came back as
 * a lone regional indicator, the keycap and the decomposed "é" lost their marks, a leading space was
 * returned as the initial, and a phone number became "+".
 */
class AvatarInitialTest {

    @Test
    fun should_upper_case_dotted_i_the_turkish_way() = assertEquals("İ", avatarInitial("ismail"))

    @Test
    fun should_upper_case_dotless_i_to_plain_I() = assertEquals("I", avatarInitial("ılgaz"))

    @Test
    fun should_keep_an_already_upper_case_letter() = assertEquals("Ş", avatarInitial("Şule"))

    @Test
    fun should_return_the_whole_flag_when_the_name_starts_with_one() {
        val turkey = "🇹🇷" // U+1F1F9 U+1F1F7
        assertEquals(turkey, avatarInitial("$turkey Ankara"))
    }

    @Test
    fun should_return_the_whole_keycap_when_the_name_starts_with_one() {
        val keycapOne = "1️⃣"
        assertEquals(keycapOne, avatarInitial("${keycapOne}numara"))
    }

    @Test
    fun should_keep_the_combining_mark_of_a_decomposed_letter() {
        // "e" + COMBINING ACUTE ACCENT: one grapheme, upper-cased on its base letter.
        assertEquals("É", avatarInitial("émilie"))
    }

    @Test
    fun should_ignore_leading_whitespace() = assertEquals("A", avatarInitial("  Ali"))

    @Test
    fun should_skip_leading_punctuation() = assertEquals("A", avatarInitial("(Ali)"))

    @Test
    fun should_have_no_initial_for_a_phone_number() = assertNull(avatarInitial("+905000000001"))

    @Test
    fun should_have_no_initial_for_an_empty_or_blank_name() {
        assertNull(avatarInitial(""))
        assertNull(avatarInitial("   "))
    }

    @Test
    fun should_keep_a_zwj_family_emoji_intact() {
        // MAN, ZWJ, WOMAN, ZWJ, GIRL — one grapheme of five code points.
        val family = "👨‍👩‍👧"
        assertEquals(family, avatarInitial("${family}Aile"))
    }

    @Test
    fun should_keep_a_skin_tone_modifier_with_its_emoji() {
        val wave = "👋🏽" // WAVING HAND + MEDIUM SKIN TONE
        assertEquals(wave, avatarInitial(wave))
    }

    @Test
    fun should_accept_a_bmp_pictograph_with_presentation_selector() {
        val heart = "❤️"
        assertEquals(heart, avatarInitial(heart + " Annem"))
    }

    @Test
    fun should_always_make_progress_even_with_a_segmenter_that_does_not() {
        // A broken segmenter that never advances must not hang the list row.
        assertEquals("A", avatarInitial("Ali") { _, start -> start })
    }
}

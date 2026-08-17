package com.muhabbet.shared.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reaction allow-list is now enforced server-side (#557), which makes its exact contents a
 * wire contract rather than a cosmetic list: a reaction whose code points change here stops
 * matching the rows already stored and starts being rejected for everyone who has the old client.
 *
 * The heart is the reason this is a test and not a comment. It is U+2764 followed by U+FE0F, and
 * that variation selector renders as nothing at all — an editor, a merge, or a copy-paste through
 * a tool that normalises Unicode can drop it silently and leave a heart that still looks like a
 * heart and no longer compares equal.
 */
class ReactionAllowListTest {

    private fun codePointsOf(s: String): List<String> =
        s.map { c -> "U+" + c.code.toString(16).uppercase().padStart(4, '0') }

    @Test
    fun `the allow list holds exactly the six reactions the bar offers, byte for byte`() {
        val expected = listOf(
            listOf("U+2764", "U+FE0F"),          // heart, with its variation selector
            listOf("U+D83D", "U+DC4D"),          // thumbs up
            listOf("U+D83D", "U+DE02"),          // tears of joy
            listOf("U+D83D", "U+DE2E"),          // face with open mouth
            listOf("U+D83D", "U+DE22"),          // crying face
            listOf("U+D83D", "U+DE4F")           // folded hands
        )

        assertEquals(
            expected,
            ValidationRules.ALLOWED_REACTIONS.map(::codePointsOf),
            "A reaction's code points changed. This is a wire contract: stored rows and older " +
                "clients use the old bytes. Add a reaction rather than editing one."
        )
    }

    @Test
    fun `every allowed reaction validates`() {
        ValidationRules.ALLOWED_REACTIONS.forEach { emoji ->
            assertTrue(ValidationRules.isValidReaction(emoji), "rejected its own entry: $emoji")
        }
    }

    @Test
    fun `arbitrary text is not a reaction`() {
        listOf(
            "",
            " ",
            "SIKTIR",
            "0123456789abcdef",       // exactly the 16 chars the column accepts
            "<script>alert(1)",
            "❤"                  // the heart WITHOUT its variation selector
        ).forEach { candidate ->
            assertFalse(
                ValidationRules.isValidReaction(candidate),
                "accepted something the picker cannot produce: ${codePointsOf(candidate)}"
            )
        }
    }
}

package com.muhabbet.designsystem.util

/**
 * The end index (exclusive) of the extended grapheme cluster that starts at [start] in [text].
 *
 * Grapheme segmentation is UAX #29, and both platforms already implement it properly, so this
 * delegates rather than approximating it (#839):
 *
 *  - **Android**: `java.text.BreakIterator.getCharacterInstance()`. On Android that class is a thin
 *    wrapper over ICU's (`android.icu.text.BreakIterator`), so it segments exactly as ICU does; on the
 *    JVM that runs the unit tests it is the JDK's own UAX #29 implementation, which since JDK 20
 *    covers emoji sequences too. Choosing it over `android.icu` directly is what lets the same
 *    test corpus run without Robolectric.
 *  - **iOS**: `NSString.rangeOfComposedCharacterSequenceAtIndex`, Foundation's composed-character
 *    segmentation.
 *
 * The previous implementation hand-rolled a subset of UAX #29 for surrogate pairs and was wrong in
 * the cases nobody wrote down: a flag split in half, a keycap or a decomposed "é" losing its mark.
 */
internal expect fun nextGraphemeBoundary(text: String, start: Int): Int

/**
 * The initial an avatar shows for [name], upper-cased the way Turkish requires, or `null` when the
 * name has nothing worth showing — the caller then draws a person glyph instead of a meaningless
 * "+" or a blank.
 *
 * The rules, in order:
 *  1. Surrounding whitespace is ignored: "  Ali" is "A", not " ".
 *  2. Leading clusters that are neither a letter nor an emoji are skipped, so "(Ali)" is "A".
 *  3. A name made only of digits and punctuation — a user shown by phone number, "+905…" — has no
 *     initial at all. Its first digit would be a different meaningless glyph for every number.
 *  4. The first qualifying cluster is returned whole: a flag, a keycap, a ZWJ family, a letter with
 *     a combining mark. Only its first letter is upper-cased, with Turkish `i`/`ı` rules.
 */
fun avatarInitial(name: String): String? = avatarInitial(name, ::nextGraphemeBoundary)

/** [avatarInitial] with the segmenter injected, so the selection rules are testable on their own. */
internal fun avatarInitial(name: String, boundary: (String, Int) -> Int): String? {
    val text = name.trim()
    var start = 0
    while (start < text.length) {
        // Defensive clamp: a segmenter must always make progress, or this loop would not end.
        val end = boundary(text, start).coerceIn(start + 1, text.length)
        val cluster = text.substring(start, end)
        if (cluster.isInitialCandidate()) return cluster.upperCaseInitial()
        start = end
    }
    return null
}

/**
 * A letter, or anything emoji-like. Supplementary-plane clusters count: outside the emoji blocks
 * they are overwhelmingly letters (CJK Extension B, historic scripts), and the emoji blocks are
 * emoji. In the BMP an emoji is recognised by its presentation selector or keycap mark, because a
 * bare `1` or `#` is a digit or punctuation, not a picture.
 */
private fun String.isInitialCandidate(): Boolean {
    val first = this[0]
    return first.isLetter() ||
        first.isHighSurrogate() ||
        any { it == EmojiPresentationSelector || it == CombiningEnclosingKeycap } ||
        first.code in MiscSymbolsAndDingbats
}

private fun String.upperCaseInitial(): String {
    val first = this[0]
    return if (first.isLetter()) first.toTurkishUpperCase() + substring(1) else this
}

/**
 * Upper-cases a single character the way Turkish requires.
 *
 * Kotlin's [Char.uppercase] is locale-*independent* — it always applies root-locale rules — so
 * `'i'.uppercase()` is `"I"`. In Turkish the capital of `i` is `İ` (U+0130) and the capital of `ı`
 * (U+0131) is `I`; getting this wrong renders "ismail" as an "I" avatar instead of "İ", which is
 * simply a misspelling of the user's name in the first letter anyone sees.
 *
 * Only the two characters that differ are special-cased; everything else is upper-cased one
 * character to one character, so "ß" stays a single glyph rather than becoming "SS" in a circle.
 */
private fun Char.toTurkishUpperCase(): String = when (this) {
    'i' -> "İ" // İ
    'ı' -> "I" // ı
    else -> uppercaseChar().toString()
}

private const val EmojiPresentationSelector = '️'
private const val CombiningEnclosingKeycap = '⃣'

/** U+2600..U+27BF: weather, chess, hearts and the dingbats — BMP pictographs that stand alone. */
private val MiscSymbolsAndDingbats = 0x2600..0x27BF

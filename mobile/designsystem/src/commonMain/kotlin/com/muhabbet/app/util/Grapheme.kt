package com.muhabbet.app.util

/**
 * Extracts the first visible grapheme from a string, upper-cased for display.
 *
 * Handles ASCII, emoji (including compound emoji with ZWJ and skin-tone modifiers), and surrogate
 * pairs. Used for avatar initials, so it must never return an empty string — `"?"` stands in for a
 * missing name.
 *
 * Lives in the design system rather than the app because it is display formatting for [UserAvatar],
 * and an `expect`-free pure function is the cheapest thing to share.
 */
fun firstGrapheme(text: String): String {
    if (text.isEmpty()) return "?"
    val ch = text[0]
    if (ch.code < 0x80) return ch.toTurkishUpperCase()
    if (ch.isHighSurrogate() && text.length > 1 && text[1].isLowSurrogate()) {
        var end = 2
        while (end < text.length) {
            val c = text[end]
            if (c == '‍') {
                end++
                if (end < text.length) {
                    end++
                    if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
                        end++
                    }
                }
            } else if (c == '️' || c == '︎') {
                end++
            } else if (c == '\uD83C' && end + 1 < text.length) {
                val low = text[end + 1]
                if (low.code in 0xDFFB..0xDFFF) {
                    end += 2
                } else break
            } else break
        }
        return text.substring(0, end)
    }
    return ch.toString()
}

/**
 * Upper-cases a single character the way Turkish requires.
 *
 * Kotlin's [Char.uppercase] is locale-*independent* — it always applies root-locale rules — so
 * `'i'.uppercase()` is `"I"`. In Turkish the capital of `i` is `İ` (U+0130) and the capital of `ı`
 * (U+0131) is `I`; getting this wrong renders "ismail" as an "I" avatar instead of "İ", which is
 * simply a misspelling of the user's name in the first letter anyone sees.
 *
 * Only the two characters that differ are special-cased; everything else defers to Kotlin.
 * Turkish is this app's default locale, so it is the right default here rather than a parameter.
 */
private fun Char.toTurkishUpperCase(): String = when (this) {
    'i' -> "İ" // İ
    'ı' -> "I" // ı
    else -> uppercase()
}

package com.muhabbet.app.util

/**
 * Extracts the first visible grapheme from a string.
 * Handles ASCII, emoji (including compound emoji with ZWJ, skin tones),
 * and surrogate pairs correctly.
 */
fun firstGrapheme(text: String): String {
    if (text.isEmpty()) return "?"
    val ch = text[0]
    if (ch.code < 0x80) return ch.uppercase()
    if (ch.isHighSurrogate() && text.length > 1 && text[1].isLowSurrogate()) {
        var end = 2
        while (end < text.length) {
            val c = text[end]
            if (c == '\u200D') {
                end++
                if (end < text.length) {
                    end++
                    if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
                        end++
                    }
                }
            } else if (c == '\uFE0F' || c == '\uFE0E') {
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
 * Applies basic message formatting:
 * *bold* → bold, _italic_ → italic, ~strikethrough~ → strikethrough, `code` → code
 * Returns an AnnotatedString for Compose rendering.
 */
data class FormattedSegment(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isCode: Boolean = false,
    val isLink: Boolean = false,
    val linkUrl: String? = null
)

private val URL_REGEX = Regex(
    """(https?://[^\s<>"]+)""",
    RegexOption.IGNORE_CASE
)

fun parseFormattedText(input: String): List<FormattedSegment> {
    val segments = mutableListOf<FormattedSegment>()
    var remaining = input

    // Simple single-pass for formatting markers
    val pattern = Regex("""(\*(.+?)\*|_(.+?)_|~(.+?)~|`(.+?)`)""")

    var lastEnd = 0
    for (match in pattern.findAll(input)) {
        if (match.range.first > lastEnd) {
            val plain = input.substring(lastEnd, match.range.first)
            segments.addAll(parseLinks(plain))
        }
        val full = match.value
        when {
            full.startsWith("*") && full.endsWith("*") ->
                segments.add(FormattedSegment(full.removeSurrounding("*"), isBold = true))
            full.startsWith("_") && full.endsWith("_") ->
                segments.add(FormattedSegment(full.removeSurrounding("_"), isItalic = true))
            full.startsWith("~") && full.endsWith("~") ->
                segments.add(FormattedSegment(full.removeSurrounding("~"), isStrikethrough = true))
            full.startsWith("`") && full.endsWith("`") ->
                segments.add(FormattedSegment(full.removeSurrounding("`"), isCode = true))
        }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < input.length) {
        segments.addAll(parseLinks(input.substring(lastEnd)))
    }

    return segments.ifEmpty { parseLinks(input) }
}

private fun parseLinks(text: String): List<FormattedSegment> {
    val segments = mutableListOf<FormattedSegment>()
    var lastEnd = 0
    for (match in URL_REGEX.findAll(text)) {
        if (match.range.first > lastEnd) {
            segments.add(FormattedSegment(text.substring(lastEnd, match.range.first)))
        }
        segments.add(FormattedSegment(match.value, isLink = true, linkUrl = match.value))
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        segments.add(FormattedSegment(text.substring(lastEnd)))
    }
    return segments
}

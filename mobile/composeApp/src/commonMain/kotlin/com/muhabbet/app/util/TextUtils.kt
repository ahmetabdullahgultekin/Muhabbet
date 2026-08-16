package com.muhabbet.app.util

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
    val linkUrl: String? = null,
    val isQuote: Boolean = false,
    val isBulletItem: Boolean = false,
    val isNumberedItem: Boolean = false,
    val listNumber: Int? = null
)

/**
 * A URL found inside a run of message text, as a range so a caller can style exactly that slice and
 * leave the surrounding sentence alone.
 */
data class UrlSpan(
    val start: Int,
    val endExclusive: Int,
    val url: String
)

/**
 * Deliberately permissive about what a URL may contain — a Turkish path such as
 * `https://site.com/ürünler` is ordinary, and an ASCII-only character class silently truncates it
 * mid-word. Everything the scan over-reaches on is removed afterwards by [trimTrailingProse].
 */
private val URL_REGEX = Regex(
    """https?://[^\s<>"]+""",
    RegexOption.IGNORE_CASE
)

/**
 * Sentence punctuation that a URL at the end of a sentence swallows and that no author meant as
 * part of the address. Closing brackets are handled separately, because they can be genuine.
 */
private const val TRAILING_PROSE = ".,;:!?…\"“”«»"

/**
 * The single place this app decides where a URL starts and ends.
 *
 * There were two regexes before — one here and one in `LinkPreviewCard` — which is how the preview
 * card could fetch a different string than the one the message displayed. Both now come through
 * here, and so does the tappable link range in `MessageBubble` (#362).
 */
fun findUrlSpans(text: String): List<UrlSpan> =
    URL_REGEX.findAll(text).mapNotNull { match ->
        val trimmed = trimTrailingProse(cutAtTurkishSuffix(match.value))
        if (trimmed.length <= "https://".length) null
        else UrlSpan(match.range.first, match.range.first + trimmed.length, trimmed)
    }.toList()

/** The first URL in [text], or null. Used to decide whether to fetch a link preview. */
fun firstUrlOrNull(text: String): String? = findUrlSpans(text).firstOrNull()?.url

/**
 * Cuts a Turkish case suffix off the end of a URL.
 *
 * Turkish attaches suffixes to proper nouns with an apostrophe, and a pasted address is a proper
 * noun: *"https://muhabbet.com'a bak"*, *"https://site.com'dan indirdim"*. The scan above swallows
 * `'a` and `'dan` because they are not whitespace, and the resulting address 404s. An apostrophe is
 * legal in a URL but vanishingly rare in a real one, so cutting at the first one is right far more
 * often than it is wrong — and when it is wrong the user still sees the full text, only the tapped
 * range is short.
 */
private fun cutAtTurkishSuffix(url: String): String =
    url.indexOfFirst { it == '\'' || it == '’' }.let { if (it < 0) url else url.take(it) }

/**
 * Removes punctuation the URL picked up from the sentence around it.
 *
 * A closing bracket is dropped only when the URL has no matching opener, so
 * `https://tr.wikipedia.org/wiki/Kedi_(hayvan)` keeps its parenthesis while `(bkz: https://x.com)`
 * does not gain one.
 */
private fun trimTrailingProse(url: String): String {
    var end = url.length
    while (end > 0) {
        val c = url[end - 1]
        val isUnmatchedCloser = when (c) {
            ')' -> url.take(end).count { it == '(' } < url.take(end).count { it == ')' }
            ']' -> url.take(end).count { it == '[' } < url.take(end).count { it == ']' }
            '}' -> url.take(end).count { it == '{' } < url.take(end).count { it == '}' }
            else -> false
        }
        if (c in TRAILING_PROSE || isUnmatchedCloser) end-- else break
    }
    return url.take(end)
}

fun parseFormattedText(input: String): List<FormattedSegment> {
    val segments = mutableListOf<FormattedSegment>()

    // First, handle line-level formatting (quotes, bullet lists, numbered lists)
    val lines = input.split("\n")
    val processedLines = mutableListOf<String>()
    var hasBlockFormatting = false

    for (line in lines) {
        val trimmedLine = line.trimStart()
        when {
            trimmedLine.startsWith("> ") -> {
                hasBlockFormatting = true
                val content = trimmedLine.removePrefix("> ")
                segments.addAll(parseInlineFormatting(content).map { it.copy(isQuote = true) })
                segments.add(FormattedSegment("\n"))
            }
            trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") && !trimmedLine.startsWith("**") -> {
                hasBlockFormatting = true
                val bullet = if (trimmedLine.startsWith("- ")) "- " else "* "
                val content = trimmedLine.removePrefix(bullet)
                segments.addAll(parseInlineFormatting(content).map { it.copy(isBulletItem = true) })
                segments.add(FormattedSegment("\n"))
            }
            trimmedLine.matches(Regex("""^\d+\.\s.+""")) -> {
                hasBlockFormatting = true
                val dotIndex = trimmedLine.indexOf(". ")
                val number = trimmedLine.substring(0, dotIndex).toIntOrNull()
                val content = trimmedLine.substring(dotIndex + 2)
                segments.addAll(parseInlineFormatting(content).map {
                    it.copy(isNumberedItem = true, listNumber = number)
                })
                segments.add(FormattedSegment("\n"))
            }
            else -> {
                processedLines.add(line)
            }
        }
    }

    // If no block formatting was found, process as inline-only
    if (!hasBlockFormatting) {
        return parseInlineFormatting(input)
    }

    // For remaining lines without block formatting, process inline
    if (processedLines.isNotEmpty()) {
        val remainingText = processedLines.joinToString("\n")
        if (remainingText.isNotBlank()) {
            segments.addAll(parseInlineFormatting(remainingText))
        }
    }

    // Remove trailing newline if present
    if (segments.isNotEmpty() && segments.last().text == "\n") {
        segments.removeAt(segments.lastIndex)
    }

    return segments.ifEmpty { parseLinks(input) }
}

private fun parseInlineFormatting(input: String): List<FormattedSegment> {
    val segments = mutableListOf<FormattedSegment>()

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
    for (span in findUrlSpans(text)) {
        if (span.start > lastEnd) {
            segments.add(FormattedSegment(text.substring(lastEnd, span.start)))
        }
        segments.add(FormattedSegment(span.url, isLink = true, linkUrl = span.url))
        lastEnd = span.endExclusive
    }
    if (lastEnd < text.length) {
        segments.add(FormattedSegment(text.substring(lastEnd)))
    }
    return segments
}

package com.muhabbet.shared.security

/**
 * Input sanitization for user-generated content.
 * Prevents XSS and injection attacks in stored content
 * that may be rendered by web/mobile clients.
 */
object InputSanitizer {

    // HTML entities that must be escaped
    private val HTML_ESCAPE_MAP = mapOf(
        '&' to "&amp;",
        '<' to "&lt;",
        '>' to "&gt;",
        '"' to "&quot;",
        '\'' to "&#x27;"
    )

    /**
     * Sanitize text content by escaping HTML special characters.
     * Used for message content, display names, group names, etc.
     */
    fun sanitizeHtml(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            sb.append(HTML_ESCAPE_MAP[c] ?: c)
        }
        return sb.toString()
    }

    /**
     * Invisible / direction-control code points that are NOT ISO control chars
     * but enable homoglyph / RTL-override / zero-width injection. Stripped by
     * [stripInvisible] / [normalizeText]. Covers: soft hyphen, zero-width
     * space/non-joiner/joiner, LRM/RLM, the LRE/RLE/PDF/LRO/RLO bidi overrides,
     * the LRI/RLI/FSI/PDI isolates, word joiner, and the BOM / zero-width
     * no-break space.
     */
    private val INVISIBLE_CODE_POINTS = setOf(
        '­',  // soft hyphen
        '​', '‌', '‍',  // zero-width space / non-joiner / joiner
        '‎', '‏',  // LRM / RLM
        '‪', '‫', '‬', '‭', '‮',  // LRE/RLE/PDF/LRO/RLO
        '⁠',  // word joiner
        '⁦', '⁧', '⁨', '⁩',  // LRI/RLI/FSI/PDI isolates
        '﻿'   // BOM / zero-width no-break space
    )

    /**
     * Strip all control characters except newlines and tabs.
     * Prevents invisible character injection.
     */
    fun stripControlChars(input: String): String {
        return input.filter { it == '\n' || it == '\t' || it == '\r' || !it.isISOControl() }
    }

    /**
     * Strip zero-width / bidirectional-override / homoglyph-enabling invisible
     * code points (see [INVISIBLE_CODE_POINTS]).
     */
    fun stripInvisible(input: String): String {
        return input.filter { it !in INVISIBLE_CODE_POINTS }
    }

    /**
     * Normalize stored free-text (display names, group/community/channel/bot
     * names, descriptions/about, status captions) at the service boundary.
     *
     * This is **input normalization, NOT output encoding**:
     * - strip control characters (preserve newlines/tabs)
     * - strip zero-width / RTL-override / invisible injection code points
     * - trim surrounding whitespace
     * - clamp to [maxLength]
     *
     * It deliberately does **NOT** HTML-escape. The only client today renders
     * plain text (Compose `Text`), so escaping `&`/`<`/`>` on input would corrupt
     * legitimate user text (e.g. "Tom & Jerry" -> "Tom &amp; Jerry"). HTML
     * escaping is an OUTPUT concern, applied by an HTML surface (a future web
     * client) at render time — see [sanitizeHtml]. Emoji and Turkish characters
     * are preserved unchanged.
     */
    fun normalizeText(input: String, maxLength: Int): String {
        return stripInvisible(stripControlChars(input)).trim().take(maxLength)
    }

    /**
     * Sanitize a display name:
     * - Trim whitespace
     * - Strip control characters
     * - Limit to maxLength
     */
    fun sanitizeDisplayName(input: String, maxLength: Int = 64): String {
        return stripControlChars(input.trim()).take(maxLength)
    }

    /**
     * Sanitize message content:
     * - Strip control characters (preserve newlines)
     * - Limit to maxLength
     */
    fun sanitizeMessageContent(input: String, maxLength: Int = 10_000): String {
        return stripControlChars(input).take(maxLength)
    }

    /**
     * Validate and sanitize a URL.
     * Only allows https:// URLs. Returns null for invalid URLs.
     */
    fun sanitizeUrl(input: String?): String? {
        if (input == null) return null
        val trimmed = input.trim()
        if (!trimmed.startsWith("https://")) return null
        // Reject URLs with common injection patterns
        if (trimmed.contains("javascript:", ignoreCase = true)) return null
        if (trimmed.contains("data:", ignoreCase = true)) return null
        return trimmed
    }
}

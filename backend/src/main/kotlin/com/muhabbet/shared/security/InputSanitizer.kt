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
     * Strip all control characters except newlines and tabs.
     * Prevents invisible character injection.
     */
    fun stripControlChars(input: String): String {
        return input.filter { it == '\n' || it == '\t' || it == '\r' || !it.isISOControl() }
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

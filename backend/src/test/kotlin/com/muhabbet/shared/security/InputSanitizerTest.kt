package com.muhabbet.shared.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class InputSanitizerTest {

    @Test
    fun `should escape HTML special characters`() {
        val input = """<script>alert("XSS")</script>"""
        val result = InputSanitizer.sanitizeHtml(input)
        assertEquals("&lt;script&gt;alert(&quot;XSS&quot;)&lt;/script&gt;", result)
    }

    @Test
    fun `should escape ampersand`() {
        assertEquals("foo &amp; bar", InputSanitizer.sanitizeHtml("foo & bar"))
    }

    @Test
    fun `should escape single quotes`() {
        assertEquals("it&#x27;s", InputSanitizer.sanitizeHtml("it's"))
    }

    @Test
    fun `should pass through safe text unchanged`() {
        assertEquals("Hello world 123", InputSanitizer.sanitizeHtml("Hello world 123"))
    }

    @Test
    fun `should strip control characters except newlines`() {
        val input = "Hello\u0000World\nLine2\u0007"
        val result = InputSanitizer.stripControlChars(input)
        assertEquals("HelloWorld\nLine2", result)
    }

    @Test
    fun `should preserve tabs and carriage returns`() {
        val input = "Col1\tCol2\r\nLine2"
        val result = InputSanitizer.stripControlChars(input)
        assertEquals("Col1\tCol2\r\nLine2", result)
    }

    @Test
    fun `should trim and limit display name`() {
        val longName = "A".repeat(100)
        val result = InputSanitizer.sanitizeDisplayName("  $longName  ")
        assertEquals(64, result.length)
    }

    @Test
    fun `should sanitize display name with control chars`() {
        val result = InputSanitizer.sanitizeDisplayName("Ali\u0000 Veli\u0007")
        assertEquals("Ali Veli", result)
    }

    @Test
    fun `should limit message content length`() {
        val longMessage = "x".repeat(20_000)
        val result = InputSanitizer.sanitizeMessageContent(longMessage)
        assertEquals(10_000, result.length)
    }

    @Test
    fun `should allow valid https URL`() {
        val url = "https://cdn.muhabbet.com/media/image.jpg"
        assertEquals(url, InputSanitizer.sanitizeUrl(url))
    }

    @Test
    fun `should reject http URL`() {
        assertNull(InputSanitizer.sanitizeUrl("http://evil.com/payload"))
    }

    @Test
    fun `should reject javascript URL`() {
        assertNull(InputSanitizer.sanitizeUrl("javascript:alert(1)"))
    }

    @Test
    fun `should reject data URL`() {
        assertNull(InputSanitizer.sanitizeUrl("data:text/html,<script>alert(1)</script>"))
    }

    @Test
    fun `should return null for null input`() {
        assertNull(InputSanitizer.sanitizeUrl(null))
    }

    @Test
    fun `should trim URL whitespace`() {
        val result = InputSanitizer.sanitizeUrl("  https://cdn.example.com/file.pdf  ")
        assertEquals("https://cdn.example.com/file.pdf", result)
    }
}

package com.muhabbet.auth.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The value that decides which language a phone is notified in (#469, V22).
 *
 * It arrives in a request body, so it is whatever the caller sent. `devices.locale` is
 * `VARCHAR(16)`: an unbounded string would turn registering a push token into a 500 from Postgres
 * rather than a field the server quietly ignored.
 */
class DeviceLocaleTest {

    @Test
    fun `should keep a plain language tag`() {
        assertEquals("tr", normalizeDeviceLocale("tr"))
        assertEquals("en", normalizeDeviceLocale("en"))
    }

    @Test
    fun `should keep a region-qualified tag`() {
        assertEquals("en-GB", normalizeDeviceLocale("en-GB"))
    }

    @Test
    fun `should canonicalise case and surrounding space`() {
        // Two devices that mean the same language must group together in OfflinePushSender, which
        // groups on this exact string — "EN" and "en" as separate keys would compose twice.
        assertEquals("en-GB", normalizeDeviceLocale("  EN-gb  "))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "!!", "12345", "en_GB"])
    fun `should reject anything that is not a language tag`(tag: String) {
        // Underscore is the Java Locale.toString() form, not BCP-47 — a client sending it is
        // guessing, and guessed input must land on the documented fallback rather than be stored.
        assertNull(normalizeDeviceLocale(tag), "Stored '$tag' as a language")
    }

    @Test
    fun `should reject null`() {
        assertNull(normalizeDeviceLocale(null))
    }

    @Test
    fun `should reject a tag too long for the column`() {
        val tooLong = "e".repeat(MAX_DEVICE_LOCALE_LENGTH + 1)

        assertNull(normalizeDeviceLocale(tooLong))
    }

    @Test
    fun `should never return a value wider than the column`() {
        val candidates = listOf("tr", "en", "en-GB", "sr-Latn-RS", "zh-Hant-TW", "  DE  ")

        candidates.mapNotNull { normalizeDeviceLocale(it) }.forEach {
            assert(it.length <= MAX_DEVICE_LOCALE_LENGTH) { "'$it' does not fit devices.locale" }
        }
    }

    @Test
    fun `should accept a language the bundles do not ship yet`() {
        // Deliberately not a whitelist of tr/en: a German device is telling the truth about itself,
        // the message source already answers it with the default bundle, and the row is then
        // already right on the day a German bundle lands.
        assertEquals("de", normalizeDeviceLocale("de"))
    }
}

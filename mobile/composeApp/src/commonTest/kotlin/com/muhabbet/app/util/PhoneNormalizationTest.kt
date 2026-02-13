package com.muhabbet.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneNormalizationTest {

    @Test
    fun should_normalize_full_e164_format() {
        assertEquals("+905321234567", normalizeToE164("+905321234567"))
    }

    @Test
    fun should_normalize_without_plus_prefix() {
        assertEquals("+905321234567", normalizeToE164("905321234567"))
    }

    @Test
    fun should_normalize_with_leading_zero() {
        assertEquals("+905321234567", normalizeToE164("05321234567"))
    }

    @Test
    fun should_normalize_without_country_code_or_zero() {
        assertEquals("+905321234567", normalizeToE164("5321234567"))
    }

    @Test
    fun should_strip_spaces() {
        assertEquals("+905321234567", normalizeToE164("+90 532 123 45 67"))
    }

    @Test
    fun should_strip_dashes() {
        assertEquals("+905321234567", normalizeToE164("+90-532-123-45-67"))
    }

    @Test
    fun should_strip_parentheses() {
        assertEquals("+905321234567", normalizeToE164("+90(532)1234567"))
    }

    @Test
    fun should_strip_mixed_separators() {
        assertEquals("+905321234567", normalizeToE164("0532 123-45-67"))
    }

    @Test
    fun should_return_null_for_too_short_number() {
        assertNull(normalizeToE164("532123"))
    }

    @Test
    fun should_return_null_for_empty_string() {
        assertNull(normalizeToE164(""))
    }

    @Test
    fun should_return_null_for_invalid_format() {
        assertNull(normalizeToE164("1234567890"))
    }

    @Test
    fun should_handle_international_non_turkish_number() {
        // International numbers with + prefix and enough digits
        assertEquals("+12125551234", normalizeToE164("+12125551234"))
    }

    @Test
    fun should_handle_test_phone_numbers() {
        // Test bot number (+905000000001 — unallocated prefix)
        assertEquals("+905000000001", normalizeToE164("+905000000001"))
        assertEquals("+905000000001", normalizeToE164("05000000001"))
        assertEquals("+905000000001", normalizeToE164("5000000001"))
    }
}

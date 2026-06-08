package com.muhabbet.shared.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract tests for the validation limits shared by backend (service layer) and mobile
 * (pre-send). These rules are a single source of truth — a backend that validated differently from
 * the client it serves would either reject valid input or accept input the client never lets
 * through, so the boundaries are pinned here.
 */
class ValidationRulesTest {

    // ─── Turkish phone (E.164, mobile prefix 5) ───────────────────────────────

    @Test
    fun turkish_phone_accepts_valid_e164() {
        assertTrue(ValidationRules.isValidTurkishPhone("+905001234567"))
    }

    @Test
    fun turkish_phone_rejects_wrong_shapes() {
        assertFalse(ValidationRules.isValidTurkishPhone("05001234567"))      // no +90
        assertFalse(ValidationRules.isValidTurkishPhone("+904001234567"))    // not a 5-prefix mobile
        assertFalse(ValidationRules.isValidTurkishPhone("+90500123456"))     // too short
        assertFalse(ValidationRules.isValidTurkishPhone("+9050012345678"))   // too long
        assertFalse(ValidationRules.isValidTurkishPhone("+90500 123 4567"))  // spaces
        assertFalse(ValidationRules.isValidTurkishPhone(""))
    }

    // ─── OTP ──────────────────────────────────────────────────────────────────

    @Test
    fun otp_must_be_exactly_six_digits() {
        assertEquals(6, ValidationRules.OTP_LENGTH)
        assertTrue(ValidationRules.isValidOtp("123456"))
        assertFalse(ValidationRules.isValidOtp("12345"))
        assertFalse(ValidationRules.isValidOtp("1234567"))
        assertFalse(ValidationRules.isValidOtp("12a456"))
    }

    // ─── Display name ───────────────────────────────────────────────────────────

    @Test
    fun display_name_respects_bounds_and_blank() {
        assertTrue(ValidationRules.isValidDisplayName("A"))
        assertTrue(ValidationRules.isValidDisplayName("a".repeat(ValidationRules.DISPLAY_NAME_MAX)))
        assertFalse(ValidationRules.isValidDisplayName("a".repeat(ValidationRules.DISPLAY_NAME_MAX + 1)))
        assertFalse(ValidationRules.isValidDisplayName("   "), "blank must be rejected")
        assertFalse(ValidationRules.isValidDisplayName(""))
    }

    // ─── About ──────────────────────────────────────────────────────────────────

    @Test
    fun about_allows_empty_and_caps_length() {
        assertTrue(ValidationRules.isValidAbout(""))
        assertTrue(ValidationRules.isValidAbout("a".repeat(ValidationRules.ABOUT_MAX)))
        assertFalse(ValidationRules.isValidAbout("a".repeat(ValidationRules.ABOUT_MAX + 1)))
    }

    // ─── Message content ──────────────────────────────────────────────────────────

    @Test
    fun message_content_rejects_blank_and_over_limit() {
        assertTrue(ValidationRules.isValidMessageContent("hi"))
        assertTrue(ValidationRules.isValidMessageContent("a".repeat(ValidationRules.MESSAGE_MAX_LENGTH)))
        assertFalse(ValidationRules.isValidMessageContent("a".repeat(ValidationRules.MESSAGE_MAX_LENGTH + 1)))
        assertFalse(ValidationRules.isValidMessageContent("   "))
        assertFalse(ValidationRules.isValidMessageContent(""))
    }

    // ─── Group name ──────────────────────────────────────────────────────────────

    @Test
    fun group_name_respects_bounds_and_blank() {
        assertTrue(ValidationRules.isValidGroupName("Aile"))
        assertTrue(ValidationRules.isValidGroupName("a".repeat(ValidationRules.GROUP_NAME_MAX)))
        assertFalse(ValidationRules.isValidGroupName("a".repeat(ValidationRules.GROUP_NAME_MAX + 1)))
        assertFalse(ValidationRules.isValidGroupName("   "))
    }

    // ─── Media size / type allowlists (consumed by backend MediaService) ─────────

    @Test
    fun media_allowlists_are_the_expected_sets() {
        assertTrue("image/jpeg" in ValidationRules.ALLOWED_IMAGE_TYPES)
        assertTrue("image/png" in ValidationRules.ALLOWED_IMAGE_TYPES)
        assertTrue("image/webp" in ValidationRules.ALLOWED_IMAGE_TYPES)
        assertFalse("image/svg+xml" in ValidationRules.ALLOWED_IMAGE_TYPES)
        assertTrue("audio/ogg" in ValidationRules.ALLOWED_VOICE_TYPES)
    }

    @Test
    fun media_size_caps_are_positive_and_ordered() {
        assertTrue(ValidationRules.MAX_IMAGE_SIZE_BYTES > 0)
        assertTrue(ValidationRules.MAX_VOICE_SIZE_BYTES > 0)
        assertTrue(ValidationRules.MAX_DOCUMENT_SIZE_BYTES >= ValidationRules.MAX_IMAGE_SIZE_BYTES)
    }
}

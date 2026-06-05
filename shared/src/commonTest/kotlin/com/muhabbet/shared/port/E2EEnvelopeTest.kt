package com.muhabbet.shared.port

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the cross-platform E2E envelope used by the message send/receive path.
 * Both Android (Signal) and any future iOS bridge serialize/parse with this exact format.
 */
class E2EEnvelopeTest {

    @Test
    fun should_encode_and_decode_round_trip() {
        val original = E2EEnvelope(ciphertext = "YWJj", senderDeviceId = "dev-1")
        val wire = E2EEnvelope.encode(original)
        // The marker must be present so the receiver can recognize it.
        assertTrue(wire.contains(E2EEnvelope.MAGIC))
        val parsed = E2EEnvelope.decodeOrNull(wire)
        assertNotNull(parsed)
        assertEquals("YWJj", parsed.ciphertext)
        assertEquals("dev-1", parsed.senderDeviceId)
        assertEquals(E2EEnvelope.MAGIC, parsed.v)
    }

    @Test
    fun should_return_null_for_plaintext_content() {
        assertNull(E2EEnvelope.decodeOrNull("Merhaba, nasılsın?"))
    }

    @Test
    fun should_return_null_for_unrelated_json() {
        // A user typing JSON-looking text must not be mistaken for an envelope.
        assertNull(E2EEnvelope.decodeOrNull("""{"hello":"world"}"""))
    }

    @Test
    fun should_return_null_for_empty_string() {
        assertNull(E2EEnvelope.decodeOrNull(""))
    }

    @Test
    fun should_decode_envelope_without_optional_device_id() {
        val wire = E2EEnvelope.encode(E2EEnvelope(ciphertext = "ZGF0YQ=="))
        val parsed = E2EEnvelope.decodeOrNull(wire)
        assertNotNull(parsed)
        assertNull(parsed.senderDeviceId)
        assertEquals("ZGF0YQ==", parsed.ciphertext)
    }

    // ─── Regression guards for the cheap MAGIC pre-check ──────────────────────
    // decodeOrNull() does a substring pre-check for MAGIC before attempting a full
    // JSON parse. These cases prove that the pre-check NEVER short-circuits into a
    // false-positive: any content that contains the marker but is not a valid envelope
    // JSON must still return null, so a user who legitimately types the marker is never
    // mistaken for ciphertext (and an E2E body is never confused with plaintext).

    @Test
    fun should_return_null_when_plaintext_merely_contains_the_marker() {
        // A user pastes/types the version marker inside an ordinary message.
        assertNull(E2EEnvelope.decodeOrNull("hey check out mhbt-e2e-1 it is the envelope tag"))
    }

    @Test
    fun should_return_null_when_content_is_only_the_marker() {
        assertNull(E2EEnvelope.decodeOrNull(E2EEnvelope.MAGIC))
    }

    @Test
    fun should_return_null_for_json_carrying_the_marker_but_no_ciphertext() {
        // Well-formed JSON, marker present, but missing the required `ciphertext` field.
        assertNull(E2EEnvelope.decodeOrNull("""{"v":"mhbt-e2e-1"}"""))
    }

    @Test
    fun should_return_null_for_envelope_with_mismatched_version() {
        // Marker substring is present (so the pre-check passes) but v is a different value:
        // a future/incompatible wire version must be rejected, not silently decrypted.
        val wire = """{"v":"mhbt-e2e-1-NEXT","ciphertext":"YWJj"}"""
        assertNull(E2EEnvelope.decodeOrNull(wire))
    }
}

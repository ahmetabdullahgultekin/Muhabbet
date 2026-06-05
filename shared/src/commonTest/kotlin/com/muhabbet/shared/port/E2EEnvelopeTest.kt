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
}

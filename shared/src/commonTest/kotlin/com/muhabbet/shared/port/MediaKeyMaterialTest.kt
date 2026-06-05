package com.muhabbet.shared.port

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for [MediaKeyMaterial] — the per-media key payload that travels inside the
 * (already-E2E-encrypted) message body. Both the sender (encode) and recipient (decode) rely on
 * this exact wire format.
 */
class MediaKeyMaterialTest {

    @Test
    fun should_encode_and_decode_round_trip() {
        val original = MediaKeyMaterial(
            keyBase64 = "a2V5",
            nonceBase64 = "bm9uY2U=",
            sha256OfCiphertextBase64 = "aGFzaA==",
            mediaId = "obj-123"
        )
        val wire = MediaKeyMaterial.encode(original)
        assertTrue(wire.contains(MediaKeyMaterial.MAGIC))
        val parsed = assertNotNull(MediaKeyMaterial.decodeOrNull(wire))
        assertEquals("a2V5", parsed.keyBase64)
        assertEquals("bm9uY2U=", parsed.nonceBase64)
        assertEquals("aGFzaA==", parsed.sha256OfCiphertextBase64)
        assertEquals("obj-123", parsed.mediaId)
        assertEquals(MediaKeyMaterial.MAGIC, parsed.v)
    }

    @Test
    fun should_return_null_for_plaintext_message() {
        // A real text message must never be mistaken for media key material.
        assertNull(MediaKeyMaterial.decodeOrNull("Merhaba, fotoğraf gönderdim"))
    }

    @Test
    fun should_return_null_for_unrelated_json() {
        assertNull(MediaKeyMaterial.decodeOrNull("""{"key":"value"}"""))
    }

    @Test
    fun should_return_null_for_e2e_envelope_payload() {
        // An E2EEnvelope (text path) is a different shape and must not parse as media key material.
        val envelope = E2EEnvelope.encode(E2EEnvelope(ciphertext = "YWJj", senderDeviceId = "d1"))
        assertNull(MediaKeyMaterial.decodeOrNull(envelope))
    }
}

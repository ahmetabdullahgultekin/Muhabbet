package com.muhabbet.shared.port

import com.muhabbet.shared.dto.DeviceLinkCompleteRequest
import com.muhabbet.shared.dto.DeviceLinkQrPayload
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The crypto boundary must be a LOUD NotYetImplemented stub — never silent, never fake crypto.
 * These tests pin that contract so nobody can accidentally ship a no-op-that-looks-encrypted.
 */
class DeviceLinkCryptoTest {

    private val crypto: DeviceLinkCrypto = NotYetImplementedDeviceLinkCrypto()

    @Test
    fun exportPublicBundle_throws_NotYetImplemented() {
        val ex = assertFailsWith<NotYetImplementedException> { crypto.exportPublicBundle() }
        assertTrue(ex.message!!.contains("libsignal"), "message must point at the libsignal block")
    }

    @Test
    fun establishSession_throws_NotYetImplemented() {
        assertFailsWith<NotYetImplementedException> { crypto.establishSession("peer", "bundle") }
    }

    @Test
    fun dropSession_throws_NotYetImplemented() {
        assertFailsWith<NotYetImplementedException> { crypto.dropSession("peer") }
    }

    // ─── DTO contract round-trips (the QR payload / complete request are the wire format) ───

    @Test
    fun qrPayload_round_trips_and_defaults_version_1() {
        val payload = DeviceLinkQrPayload(linkToken = "abc-123", apiBaseUrl = "https://api.example")
        assertEquals(1, payload.v)
        val json = Json.encodeToString(DeviceLinkQrPayload.serializer(), payload)
        val back = Json.decodeFromString(DeviceLinkQrPayload.serializer(), json)
        assertEquals(payload, back)
    }

    @Test
    fun completeRequest_publicBundle_is_optional() {
        // A companion can complete a link WITHOUT a bundle while the crypto slice is blocked.
        val json = """{"linkToken":"t","platform":"web"}"""
        val req = Json.decodeFromString(DeviceLinkCompleteRequest.serializer(), json)
        assertEquals("t", req.linkToken)
        assertEquals("web", req.platform)
        assertEquals(null, req.publicBundle)
    }
}

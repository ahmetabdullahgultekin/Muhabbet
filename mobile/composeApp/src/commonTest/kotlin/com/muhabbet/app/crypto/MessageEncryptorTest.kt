package com.muhabbet.app.crypto

import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.port.E2EEnvelope
import com.muhabbet.shared.port.EncryptionPort
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trip and fallback tests for the E2E message wiring.
 *
 * Uses a reversible fake [EncryptionPort] (byte-wise NOT, which is its own inverse) so the test
 * proves the encrypt-on-send / decrypt-on-receive plumbing — envelope encode/decode, Base64,
 * recipient resolution, session gating, and flag behavior — independently of libsignal.
 */
class MessageEncryptorTest {

    /** Reversible cipher: invert each byte. encrypt(encrypt(x)) == x. */
    private class FlipEncryption : EncryptionPort {
        var encryptCalls = 0
        var decryptCalls = 0
        override suspend fun encrypt(content: ByteArray, recipientId: String, deviceId: String): ByteArray {
            encryptCalls++
            return ByteArray(content.size) { (content[it].toInt().inv() and 0xFF).toByte() }
        }
        override suspend fun decrypt(content: ByteArray, senderId: String, deviceId: String): ByteArray {
            decryptCalls++
            return ByteArray(content.size) { (content[it].toInt().inv() and 0xFF).toByte() }
        }
    }

    private fun sendMessage(
        content: String,
        contentType: ContentType = ContentType.TEXT,
        conversationId: String = "conv-direct"
    ) = WsMessage.SendMessage(
        requestId = "req-1",
        messageId = "msg-1",
        conversationId = conversationId,
        content = content,
        contentType = contentType
    )

    private fun newMessage(content: String) = WsMessage.NewMessage(
        messageId = "msg-1",
        conversationId = "conv-direct",
        senderId = "peer-id",
        senderName = "Peer",
        content = content,
        contentType = ContentType.TEXT,
        serverTimestamp = 0L
    )

    private fun encryptor(
        port: EncryptionPort,
        enabled: Boolean = true,
        resolverNull: Boolean = false,
        sessionReady: Boolean = true
    ) = MessageEncryptor(
        encryptionPort = port,
        recipientResolver = { convId ->
            if (resolverNull || convId == "conv-group") null
            else MessageEncryptor.RecipientInfo(recipientId = "peer-id", deviceId = "1")
        },
        ensureSession = { sessionReady },
        selfDeviceId = { "device-self" },
        enabled = enabled
    )

    // ─── Core round-trip ────────────────────────────────────────────────

    @Test
    fun should_round_trip_plaintext_through_encrypt_then_decrypt() = runTest {
        val port = FlipEncryption()
        val enc = encryptor(port)
        val original = "Merhaba dünya — gizli mesaj 🔐"

        val sent = enc.encryptOutgoing(sendMessage(original))
        // On the wire the body must NOT be the plaintext.
        assertNotEquals(original, sent.content)
        // The wire body must be a recognizable E2E envelope.
        val envelope = E2EEnvelope.decodeOrNull(sent.content)
        assertTrue(envelope != null, "encrypted body should be an E2EEnvelope")
        assertEquals("device-self", envelope.senderDeviceId)

        // Receiver decrypts the same envelope back to the original plaintext.
        val received = enc.decryptIncoming(newMessage(sent.content))
        assertEquals(original, received.content)
        assertEquals(1, port.encryptCalls)
        assertEquals(1, port.decryptCalls)
    }

    @Test
    fun should_produce_ciphertext_that_is_not_readable_as_plaintext() = runTest {
        val port = FlipEncryption()
        val sent = encryptor(port).encryptOutgoing(sendMessage("attack at dawn"))
        // The literal plaintext must not appear anywhere in the serialized body.
        assertFalse(sent.content.contains("attack at dawn"))
    }

    // ─── Feature flag (default OFF preserves legacy behavior) ────────────

    @Test
    fun should_pass_through_unchanged_when_disabled() = runTest {
        val port = FlipEncryption()
        val enc = encryptor(port, enabled = false)
        val msg = sendMessage("plain body")

        val sent = enc.encryptOutgoing(msg)
        assertEquals(msg, sent)                       // byte-identical, no copy of content
        assertEquals(0, port.encryptCalls)            // crypto never invoked

        val received = enc.decryptIncoming(newMessage("plain body"))
        assertEquals("plain body", received.content)
        assertEquals(0, port.decryptCalls)
    }

    // ─── Eligibility / graceful fallback ─────────────────────────────────

    @Test
    fun should_skip_encryption_for_non_text_content() = runTest {
        val port = FlipEncryption()
        val msg = sendMessage("https://media/x.jpg", contentType = ContentType.IMAGE)
        val sent = encryptor(port).encryptOutgoing(msg)
        assertEquals(msg.content, sent.content)
        assertEquals(0, port.encryptCalls)
    }

    @Test
    fun should_skip_encryption_when_recipient_cannot_be_resolved() = runTest {
        val port = FlipEncryption()
        val msg = sendMessage("hi", conversationId = "conv-group") // resolver returns null
        val sent = encryptor(port).encryptOutgoing(msg)
        assertEquals(msg.content, sent.content)
        assertEquals(0, port.encryptCalls)
    }

    @Test
    fun should_skip_encryption_when_session_not_ready() = runTest {
        val port = FlipEncryption()
        val sent = encryptor(port, sessionReady = false).encryptOutgoing(sendMessage("hi"))
        assertEquals("hi", sent.content)
        assertEquals(0, port.encryptCalls)
    }

    @Test
    fun should_not_double_encrypt_an_existing_envelope() = runTest {
        val port = FlipEncryption()
        val enc = encryptor(port)
        val once = enc.encryptOutgoing(sendMessage("hello"))
        // Re-running (e.g. offline-queue resend) must not wrap it twice.
        val twice = enc.encryptOutgoing(once)
        assertEquals(once.content, twice.content)
        assertEquals(1, port.encryptCalls)
    }

    // ─── Decrypt-side robustness ─────────────────────────────────────────

    @Test
    fun should_leave_plaintext_incoming_message_untouched() = runTest {
        val port = FlipEncryption()
        val received = encryptor(port).decryptIncoming(newMessage("just plain text"))
        assertEquals("just plain text", received.content)
        assertEquals(0, port.decryptCalls)
    }

    @Test
    fun should_return_null_envelope_for_plaintext() {
        assertNull(E2EEnvelope.decodeOrNull("hello world"))
        assertNull(E2EEnvelope.decodeOrNull("""{"foo":"bar"}"""))
    }
}

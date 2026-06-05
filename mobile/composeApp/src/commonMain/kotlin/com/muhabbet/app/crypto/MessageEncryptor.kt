package com.muhabbet.app.crypto

import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.port.E2EEnvelope
import com.muhabbet.shared.port.EncryptionPort
import com.muhabbet.shared.protocol.WsMessage
import kotlin.io.encoding.Base64

/**
 * Wires [EncryptionPort] into the actual message send/receive path.
 *
 * This is the missing seam called out in the roadmap: keys were registered and Signal sessions
 * could be set up, but no code path ever called `.encrypt()`/`.decrypt()` on a message body.
 * [MessageEncryptor] is invoked from the single send chokepoint ([com.muhabbet.app.data.remote.WsClient.send])
 * and the single receive chokepoint (the WsClient incoming frame loop), so every existing UI
 * call site is covered without touching them.
 *
 * Scope (deliberately conservative for the first cut — see PR remaining-checklist):
 *  - Only [ContentType.TEXT] bodies in DIRECT (1:1) conversations are encrypted. Media/voice/etc.
 *    reference bytes already uploaded to MinIO; encrypting the blobs is a separate, larger task.
 *  - Group conversations are skipped (Signal sessions are pairwise; sender-key fan-out is future work).
 *  - Encryption requires a resolvable recipient + an established session. If anything is missing,
 *    the body is sent as plaintext (graceful fallback) — never dropped, never thrown.
 *
 * All behavior is gated by [E2EConfig.ENABLED]; when false this class is a pass-through and the
 * path is byte-identical to current production.
 */
class MessageEncryptor(
    private val encryptionPort: EncryptionPort,
    /** Resolve a conversationId to its remote peer; null = not a 1:1 / unknown (skip E2E). */
    private val recipientResolver: suspend (conversationId: String) -> RecipientInfo?,
    /** Ensure a Signal session exists with the recipient; true = ready to encrypt. */
    private val ensureSession: suspend (recipientId: String) -> Boolean,
    /** This device's id, embedded in the envelope so the peer selects the right session. */
    private val selfDeviceId: () -> String?,
    private val enabled: Boolean = E2EConfig.ENABLED
) {

    data class RecipientInfo(val recipientId: String, val deviceId: String)

    /**
     * Encrypt an outgoing [WsMessage.SendMessage] in place, returning a copy whose [content]
     * holds an [E2EEnvelope] string. Returns the original message unchanged when E2E is disabled,
     * the message is not eligible, or anything in the encryption chain fails.
     */
    suspend fun encryptOutgoing(message: WsMessage.SendMessage): WsMessage.SendMessage {
        if (!enabled) return message
        if (message.contentType != ContentType.TEXT) return message
        if (message.content.isEmpty()) return message
        // Already an envelope (e.g. re-send from the offline queue) — don't double-encrypt.
        if (E2EEnvelope.decodeOrNull(message.content) != null) return message

        return try {
            val recipient = recipientResolver(message.conversationId) ?: return message
            if (!ensureSession(recipient.recipientId)) return message

            val cipherBytes = encryptionPort.encrypt(
                content = message.content.encodeToByteArray(),
                recipientId = recipient.recipientId,
                deviceId = recipient.deviceId
            )
            val envelope = E2EEnvelope(
                ciphertext = Base64.Default.encode(cipherBytes),
                senderDeviceId = selfDeviceId()
            )
            message.copy(content = E2EEnvelope.encode(envelope))
        } catch (_: Exception) {
            // Never block a send on encryption failure — fall back to the original body.
            message
        }
    }

    /**
     * Decrypt an incoming [WsMessage.NewMessage] in place if its [content] is an [E2EEnvelope].
     * Returns the message unchanged when E2E is disabled, the body is plaintext, or decryption
     * fails (the envelope is then left as-is so the failure is visible rather than silently lost).
     */
    suspend fun decryptIncoming(message: WsMessage.NewMessage): WsMessage.NewMessage {
        if (!enabled) return message
        val envelope = E2EEnvelope.decodeOrNull(message.content) ?: return message

        return try {
            val cipherBytes = Base64.Default.decode(envelope.ciphertext)
            val plainBytes = encryptionPort.decrypt(
                content = cipherBytes,
                senderId = message.senderId,
                deviceId = envelope.senderDeviceId ?: ""
            )
            message.copy(content = plainBytes.decodeToString())
        } catch (_: Exception) {
            message
        }
    }
}

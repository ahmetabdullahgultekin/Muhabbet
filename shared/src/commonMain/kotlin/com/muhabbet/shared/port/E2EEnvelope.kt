package com.muhabbet.shared.port

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Self-describing envelope for end-to-end-encrypted message bodies.
 *
 * Why an envelope?
 * - The WebSocket [com.muhabbet.shared.protocol.WsMessage.SendMessage.content] field is a
 *   plain `String`. When E2E is enabled the sender replaces the plaintext with the JSON
 *   serialization of this envelope; the receiver detects the [MAGIC] marker, decrypts, and
 *   substitutes the plaintext back before the message reaches the UI.
 * - Plaintext (legacy / flag-off / unsupported content) is left completely untouched, so the
 *   change is backward- and forward-compatible: an old client or a plaintext message simply
 *   never matches [MAGIC] and is passed through verbatim.
 *
 * The [ciphertext] is Base64 of the raw bytes produced by [EncryptionPort.encrypt]; the
 * underlying bytes are whatever the active [EncryptionPort] yields (Signal/libsignal on
 * Android, NoOp elsewhere). This type carries NO key material — only opaque ciphertext.
 */
@Serializable
data class E2EEnvelope(
    /** Marker so the receiver can distinguish an encrypted body from arbitrary plaintext. */
    val v: String = MAGIC,
    /** Base64-encoded ciphertext bytes from [EncryptionPort.encrypt]. */
    val ciphertext: String,
    /** Sender's device id — needed by the recipient to select the right Signal session. */
    val senderDeviceId: String? = null
) {
    companion object {
        /** Envelope marker / version. Bump if the wire format changes incompatibly. */
        const val MAGIC = "mhbt-e2e-1"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = false
        }

        /** Serialize an envelope to the string that travels in `SendMessage.content`. */
        fun encode(envelope: E2EEnvelope): String = json.encodeToString(envelope)

        /**
         * Attempt to parse [content] as an [E2EEnvelope].
         *
         * Returns null when the content is NOT an encrypted envelope (the common, fast path
         * for plaintext) — callers must treat null as "leave the content untouched". Parsing
         * is intentionally conservative: it only succeeds when the [MAGIC] marker matches, so a
         * user who legitimately types a JSON-looking message is never misinterpreted.
         */
        fun decodeOrNull(content: String): E2EEnvelope? {
            // Cheap pre-check before attempting a full JSON parse.
            if (!content.contains(MAGIC)) return null
            return try {
                val envelope = json.decodeFromString<E2EEnvelope>(content)
                if (envelope.v == MAGIC) envelope else null
            } catch (_: Exception) {
                null
            }
        }
    }
}

package com.muhabbet.shared.port

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The per-media symmetric key material that lets a recipient decrypt an E2E media blob.
 *
 * WhatsApp-style media E2E flow:
 *  1. Sender generates a **fresh** AES-256 key + 12-byte nonce per media, encrypts the
 *     (already-compressed) bytes, and uploads only the **ciphertext** to MinIO. The server/storage
 *     never sees plaintext.
 *  2. This [MediaKeyMaterial] (key + nonce + ciphertext hash — never the bytes) travels to the
 *     recipient **inside the message content**, which is itself end-to-end encrypted by the existing
 *     text path (`MessageEncryptor` → Signal). So the media key is protected by Signal and never
 *     placed in any server-readable field.
 *  3. Recipient fetches the ciphertext blob, verifies [sha256OfCiphertextBase64] (at-rest integrity),
 *     then AES-GCM-decrypts with [keyBase64] + [nonceBase64]. AES-GCM is authenticated, so a tampered
 *     blob or wrong key fails closed.
 *
 * This type carries the key, so it MUST only ever be serialized into an already-E2E-encrypted
 * message body — never into a plaintext field, a URL, or anything the server can read. It lives in
 * the shared module because both the sender (encode) and receiver (decode) need the same contract,
 * and it is intentionally separate from [E2EEnvelope] (which carries opaque ciphertext only).
 */
@Serializable
data class MediaKeyMaterial(
    /** Marker so a receiver can distinguish a media-key payload from a plain text body. */
    val v: String = MAGIC,
    /** Base64 of the fresh AES-256 key (32 bytes). */
    val keyBase64: String,
    /** Base64 of the fresh 96-bit GCM nonce (12 bytes). */
    val nonceBase64: String,
    /** Base64 SHA-256 of the uploaded ciphertext blob, for at-rest integrity verification. */
    val sha256OfCiphertextBase64: String,
    /** The MinIO object key / media id the ciphertext was uploaded under (server-side reference). */
    val mediaId: String? = null
) {
    companion object {
        /** Payload marker / version. Bump if the layout changes incompatibly. */
        const val MAGIC = "mhbt-media-1"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = false
        }

        /** Serialize to the string carried inside the (E2E-encrypted) message content. */
        fun encode(material: MediaKeyMaterial): String = json.encodeToString(material)

        /**
         * Parse [content] as [MediaKeyMaterial], or null when it is not a media-key payload
         * (the common case: a plain text body). Conservative — only succeeds when [MAGIC] matches,
         * so a user typing JSON-looking text is never misinterpreted as media key material.
         */
        fun decodeOrNull(content: String): MediaKeyMaterial? {
            if (!content.contains(MAGIC)) return null
            return try {
                val material = json.decodeFromString<MediaKeyMaterial>(content)
                if (material.v == MAGIC) material else null
            } catch (_: Exception) {
                null
            }
        }
    }
}

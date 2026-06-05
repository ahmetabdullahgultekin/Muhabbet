package com.muhabbet.app.crypto

import com.muhabbet.shared.port.MediaKeyMaterial
import kotlin.io.encoding.Base64

/**
 * WhatsApp-style media-blob end-to-end encryption (Tier 1.4).
 *
 * Closes the gap left by [MessageEncryptor], whose scope comment notes that media-blob encryption
 * is "a separate, larger task". [MessageEncryptor] protects TEXT bodies via Signal; this protects
 * the **media bytes themselves** so the server/MinIO only ever stores ciphertext.
 *
 * Flow (only when [E2EConfig.mediaEncryptionActive]):
 *  1. [encryptForUpload] generates a fresh AES-256 key + 12-byte nonce per media, AES-GCM-encrypts
 *     the (already-compressed) bytes, and returns the **ciphertext** blob plus a small
 *     [MediaKeyMaterial] (key, nonce, SHA-256 of the ciphertext). Only the ciphertext is uploaded.
 *  2. The caller places the [MediaKeyMaterial] inside the message content, which is then E2E-encrypted
 *     by the existing [MessageEncryptor] text path — so the key is protected by Signal and never put
 *     in any server-readable field.
 *  3. [decryptDownloaded] verifies the ciphertext SHA-256 (at-rest integrity), then AES-GCM-decrypts.
 *     AES-GCM is authenticated: a tampered blob or wrong key throws — it never returns garbage as if
 *     it were valid plaintext.
 *
 * Backward compatibility / graceful fallback (mirrors [MessageEncryptor]):
 *  - When the flag is OFF, [encryptForUpload] returns the bytes untouched and no key material — the
 *    upload path is byte-identical to today (plaintext blobs).
 *  - If anything in the encrypt chain fails (e.g. iOS NoOp throws), [encryptForUpload] returns the
 *    plaintext bytes + null key material so the caller uploads plaintext: the media is never dropped
 *    and the send never crashes.
 *  - On download, a message with no [MediaKeyMaterial] (legacy / plaintext media) is returned as-is.
 *    A message WITH key material whose blob fails integrity/auth throws [MediaDecryptException] so the
 *    UI can show a visible decrypt-failed state — never silent corruption.
 *
 * SRP: this class only does per-media symmetric crypto + key-material packaging. It does not upload,
 * download, compress, or know about MinIO/HTTP — the caller wires those.
 */
class MediaEncryptor(
    private val cipher: SymmetricCipherGateway = DefaultSymmetricCipherGateway,
    private val enabled: Boolean = E2EConfig.mediaEncryptionActive
) {

    /**
     * Result of [encryptForUpload]. When [keyMaterial] is null the [blob] is plaintext (flag OFF or
     * graceful fallback) and must be uploaded as today; when non-null the [blob] is ciphertext and
     * the [keyMaterial] must travel inside the E2E-encrypted message body.
     */
    data class EncryptedMedia(
        val blob: ByteArray,
        val keyMaterial: MediaKeyMaterial?
    ) {
        val isEncrypted: Boolean get() = keyMaterial != null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedMedia) return false
            return blob.contentEquals(other.blob) && keyMaterial == other.keyMaterial
        }

        override fun hashCode(): Int = blob.contentHashCode() * 31 + (keyMaterial?.hashCode() ?: 0)
    }

    /**
     * Encrypt [compressedBytes] for upload. When E2E media is disabled, returns the bytes unchanged
     * with null key material (caller uploads plaintext, byte-identical to today). On any crypto
     * failure, falls back to plaintext + null key material — never throws, never drops the media.
     *
     * @param mediaId optional MinIO object key, echoed into [MediaKeyMaterial.mediaId] when known.
     */
    fun encryptForUpload(compressedBytes: ByteArray, mediaId: String? = null): EncryptedMedia {
        if (!enabled) return EncryptedMedia(compressedBytes, null)
        return try {
            val key = cipher.generateKey()
            val nonce = cipher.generateNonce()
            val ciphertext = cipher.encrypt(compressedBytes, key, nonce)
            val material = MediaKeyMaterial(
                keyBase64 = Base64.Default.encode(key),
                nonceBase64 = Base64.Default.encode(nonce),
                sha256OfCiphertextBase64 = Base64.Default.encode(cipher.sha256(ciphertext)),
                mediaId = mediaId
            )
            EncryptedMedia(ciphertext, material)
        } catch (_: Throwable) {
            // Never block a send on encryption failure (incl. iOS NoOp) — upload plaintext instead.
            EncryptedMedia(compressedBytes, null)
        }
    }

    /**
     * Decrypt a downloaded [blob] using [keyMaterial] recovered from the (decrypted) message body.
     *
     * - [keyMaterial] null → legacy/plaintext media: returns [blob] unchanged.
     * - integrity mismatch (SHA-256 of [blob] ≠ recorded hash) → throws [MediaDecryptException]
     *   (the blob was altered at rest).
     * - AES-GCM auth failure (tampered ciphertext / wrong key) → the cipher throws; wrapped into
     *   [MediaDecryptException]. Decryption FAILS CLOSED — never returns unauthenticated bytes.
     */
    fun decryptDownloaded(blob: ByteArray, keyMaterial: MediaKeyMaterial?): ByteArray {
        if (keyMaterial == null) return blob // legacy / plaintext media renders as before
        return try {
            val expectedHash = Base64.Default.decode(keyMaterial.sha256OfCiphertextBase64)
            val actualHash = cipher.sha256(blob)
            if (!actualHash.contentEquals(expectedHash)) {
                throw MediaDecryptException("MEDIA_INTEGRITY_MISMATCH")
            }
            val key = Base64.Default.decode(keyMaterial.keyBase64)
            val nonce = Base64.Default.decode(keyMaterial.nonceBase64)
            cipher.decrypt(blob, key, nonce)
        } catch (e: MediaDecryptException) {
            throw e
        } catch (e: Throwable) {
            // AES-GCM tag failure, bad Base64, etc. — fail closed with a visible error.
            throw MediaDecryptException("MEDIA_DECRYPT_FAILED", e)
        }
    }
}

/** Thrown when a downloaded media blob cannot be authentically decrypted (tamper / wrong key). */
class MediaDecryptException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thin seam over the [SymmetricCipher] expect/actual object so [MediaEncryptor] is unit-testable on
 * the JVM with a fake (the `object` expect can't be substituted directly). [DefaultSymmetricCipherGateway]
 * delegates to the real platform crypto.
 */
interface SymmetricCipherGateway {
    fun generateKey(): ByteArray
    fun generateNonce(): ByteArray
    fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray
    fun decrypt(ciphertextAndTag: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray
    fun sha256(data: ByteArray): ByteArray
}

/** Production gateway — delegates to the platform [SymmetricCipher] (Android JCE / iOS NoOp). */
object DefaultSymmetricCipherGateway : SymmetricCipherGateway {
    override fun generateKey(): ByteArray = SymmetricCipher.generateKey()
    override fun generateNonce(): ByteArray = SymmetricCipher.generateNonce()
    override fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray =
        SymmetricCipher.encrypt(plaintext, key, nonce)
    override fun decrypt(ciphertextAndTag: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray =
        SymmetricCipher.decrypt(ciphertextAndTag, key, nonce)
    override fun sha256(data: ByteArray): ByteArray = SymmetricCipher.sha256(data)
}

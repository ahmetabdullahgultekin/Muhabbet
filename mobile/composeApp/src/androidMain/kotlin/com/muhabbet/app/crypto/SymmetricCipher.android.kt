package com.muhabbet.app.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android AES-256-GCM symmetric crypto via the JCE (`javax.crypto`).
 *
 * - `AES/GCM/NoPadding`, 128-bit (16-byte) authentication tag.
 * - Keys/nonces come from [SecureRandom] (platform CSPRNG).
 * - JCE appends the GCM tag to the ciphertext on `doFinal`, so [encrypt] returns `ciphertext || tag`
 *   and [decrypt] expects that same layout. `Cipher.doFinal` throws `AEADBadTagException` (a
 *   `GeneralSecurityException`) when the tag does not verify — i.e. tamper / wrong key fail closed.
 */
actual object SymmetricCipher {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val GCM_TAG_BITS = 128

    actual val KEY_SIZE_BYTES: Int = 32   // AES-256
    actual val NONCE_SIZE_BYTES: Int = 12 // 96-bit GCM IV

    private val random = SecureRandom()

    actual fun generateKey(): ByteArray = ByteArray(KEY_SIZE_BYTES).also { random.nextBytes(it) }

    actual fun generateNonce(): ByteArray = ByteArray(NONCE_SIZE_BYTES).also { random.nextBytes(it) }

    actual fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "MEDIA_KEY_SIZE_INVALID" }
        require(nonce.size == NONCE_SIZE_BYTES) { "MEDIA_NONCE_SIZE_INVALID" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, KEY_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )
        return cipher.doFinal(plaintext)
    }

    actual fun decrypt(ciphertextAndTag: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "MEDIA_KEY_SIZE_INVALID" }
        require(nonce.size == NONCE_SIZE_BYTES) { "MEDIA_NONCE_SIZE_INVALID" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, KEY_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )
        // Throws AEADBadTagException on a wrong key / tampered ciphertext / truncated tag.
        return cipher.doFinal(ciphertextAndTag)
    }

    actual fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}

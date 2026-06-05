package com.muhabbet.app.crypto

/**
 * Authenticated symmetric crypto for media-blob E2E (AES-256-GCM).
 *
 * Mirrors the existing `ShareLauncher` / `ImageCompressor` expect/actual platform-seam pattern:
 * the contract lives in commonMain, the real implementation is per-platform.
 *  - **Android** (`SymmetricCipher.android.kt`): `javax.crypto.Cipher` `AES/GCM/NoPadding`,
 *    128-bit auth tag, `SecureRandom` CSPRNG.
 *  - **iOS** (`SymmetricCipher.ios.kt`): NoOp stub that throws, consistent with how iOS Signal is
 *    currently `NoOpEncryption` (no libsignal bridge yet). Media E2E is therefore Android-only for
 *    now — same posture as the text path.
 *
 * AES-GCM gives **authenticated** decryption: a tampered ciphertext or a wrong key MUST fail
 * (the actual throws), never return garbage as if valid. Callers ([MediaEncryptor]) translate that
 * failure into a visible decrypt-failed state — media is never silently corrupted.
 *
 * Contract:
 *  - [generateKey] returns 32 fresh random bytes (AES-256). Never reuse a key across media.
 *  - [generateNonce] returns 12 fresh random bytes (96-bit GCM IV). Never reuse a nonce under a key.
 *  - [encrypt] returns `ciphertext || tag` (the JCE-standard layout: the 16-byte GCM tag is
 *    appended to the ciphertext).
 *  - [decrypt] verifies the tag and throws on any mismatch (tamper / wrong key / truncation).
 *  - [sha256] hashes the ciphertext blob so the download path can detect at-rest corruption
 *    *before* attempting authenticated decryption (defence in depth + a clearer failure surface).
 */
expect object SymmetricCipher {

    /** AES-256 key length in bytes. */
    val KEY_SIZE_BYTES: Int

    /** GCM nonce/IV length in bytes (96-bit, the GCM-recommended size). */
    val NONCE_SIZE_BYTES: Int

    /** Fresh CSPRNG key — [KEY_SIZE_BYTES] bytes. */
    fun generateKey(): ByteArray

    /** Fresh CSPRNG nonce — [NONCE_SIZE_BYTES] bytes. Never reuse under the same key. */
    fun generateNonce(): ByteArray

    /**
     * AES-256-GCM encrypt. Returns `ciphertext || authTag` (16-byte tag appended), the standard
     * JCE output layout. [key] must be [KEY_SIZE_BYTES]; [nonce] must be [NONCE_SIZE_BYTES].
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray

    /**
     * AES-256-GCM authenticated decrypt of `ciphertext || authTag`. Throws on tag mismatch
     * (tampered blob, wrong key, or truncation) — never returns unauthenticated bytes.
     */
    fun decrypt(ciphertextAndTag: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray

    /** SHA-256 of [data], returned as raw 32 bytes. */
    fun sha256(data: ByteArray): ByteArray
}

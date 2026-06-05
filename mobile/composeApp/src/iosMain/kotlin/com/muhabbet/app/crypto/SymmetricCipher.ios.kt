package com.muhabbet.app.crypto

/**
 * iOS AES-256-GCM symmetric crypto — **NoOp stub** for now.
 *
 * This mirrors the current iOS E2E posture: iOS uses `NoOpEncryption` for the text path because the
 * libsignal Kotlin/Native bridge has not landed yet (see `PlatformModule.ios.kt`). Media E2E is
 * therefore Android-only at this stage.
 *
 * Every method throws [NotImplementedError] instead of returning the plaintext. That is deliberate:
 * a NoOp that returned plaintext would mean "encrypted" blobs upload in the clear while the message
 * claims they are encrypted — a silent security downgrade. By throwing, the [MediaEncryptor] chain
 * fails closed and falls back to the legacy plaintext-upload path (no `MediaKeyMaterial` in the
 * message), which is honest and matches today's behavior. Replace with CryptoKit
 * (`AES.GCM.seal` / `AES.GCM.open`) when wiring iOS media E2E (Tier 2 / iOS parity).
 */
actual object SymmetricCipher {

    actual val KEY_SIZE_BYTES: Int = 32
    actual val NONCE_SIZE_BYTES: Int = 12

    actual fun generateKey(): ByteArray =
        throw NotImplementedError("MEDIA_E2E_IOS_NOT_IMPLEMENTED")

    actual fun generateNonce(): ByteArray =
        throw NotImplementedError("MEDIA_E2E_IOS_NOT_IMPLEMENTED")

    actual fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray =
        throw NotImplementedError("MEDIA_E2E_IOS_NOT_IMPLEMENTED")

    actual fun decrypt(ciphertextAndTag: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray =
        throw NotImplementedError("MEDIA_E2E_IOS_NOT_IMPLEMENTED")

    actual fun sha256(data: ByteArray): ByteArray =
        throw NotImplementedError("MEDIA_E2E_IOS_NOT_IMPLEMENTED")
}

package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.util.sha256Hex
import kotlin.random.Random

/**
 * Persistence + PIN logic for **Mahrem Mod (Privacy Mode)**.
 *
 * All state is device-local (never synced to the server) and stored through [TokenStorage]:
 *  - The PIN is stored as a **salted, stretched SHA-256 hash** — the plaintext PIN is never
 *    persisted. The hash + its per-install random salt live in the platform's encrypted secure store
 *    (Android `EncryptedSharedPreferences`, iOS Keychain).
 *  - The toggles (hide-preview, screenshot-guard, app-lock-enabled) live in plain prefs so the
 *    notification builder can read the preview flag before crypto init.
 *
 * Hashing: `salt` is 16 random bytes (hex). The stored value is [HASH_ROUNDS] iterations of
 * `sha256Hex(salt + pin + previousDigest)`. This is deliberately simple (KISS) and depends on no
 * crypto-block library — it is a local device-lock deterrent, not transport/E2E crypto, so it does
 * NOT touch the libsignal/E2E seam.
 */
class PrivacyModeRepository(
    private val tokenStorage: TokenStorage
) {
    fun isPreviewHidden(): Boolean = tokenStorage.getHideNotificationPreview()

    fun setPreviewHidden(enabled: Boolean) = tokenStorage.setHideNotificationPreview(enabled)

    fun isScreenshotGuardEnabled(): Boolean = tokenStorage.getScreenshotGuardEnabled()

    fun setScreenshotGuardEnabled(enabled: Boolean) = tokenStorage.setScreenshotGuardEnabled(enabled)

    fun isAppLockEnabled(): Boolean = tokenStorage.getAppLockEnabled()

    /** A PIN is set when the app-lock toggle is on AND a hash has been stored. */
    fun isPinSet(): Boolean =
        tokenStorage.getAppLockEnabled() && tokenStorage.getPrivacyPinHash() != null

    /**
     * Stores a freshly-hashed PIN (new random salt) and enables app-lock. Returns false for an
     * invalid PIN (too short/long or non-digit) so the caller can surface an error.
     */
    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = randomSaltHex()
        tokenStorage.setPrivacyPinSalt(salt)
        tokenStorage.setPrivacyPinHash(hashPin(pin, salt))
        tokenStorage.setAppLockEnabled(true)
        return true
    }

    /** Verifies a candidate PIN against the stored salted hash. */
    fun verifyPin(pin: String): Boolean {
        val salt = tokenStorage.getPrivacyPinSalt() ?: return false
        val stored = tokenStorage.getPrivacyPinHash() ?: return false
        return hashPin(pin, salt) == stored
    }

    /** Clears the PIN + salt and disables app-lock (e.g. when the user turns the lock off). */
    fun clearPin() {
        tokenStorage.setPrivacyPinHash(null)
        tokenStorage.setPrivacyPinSalt(null)
        tokenStorage.setAppLockEnabled(false)
    }

    private fun randomSaltHex(): String {
        val bytes = Random.nextBytes(SALT_BYTES)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private fun hashPin(pin: String, salt: String): String {
        var digest = ""
        repeat(HASH_ROUNDS) {
            digest = sha256Hex(salt + pin + digest)
        }
        return digest
    }

    companion object {
        const val PIN_MIN_LENGTH = 4
        const val PIN_MAX_LENGTH = 8
        private const val SALT_BYTES = 16
        private const val HASH_ROUNDS = 10_000

        fun isValidPin(pin: String): Boolean =
            pin.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH && pin.all { it.isDigit() }
    }
}

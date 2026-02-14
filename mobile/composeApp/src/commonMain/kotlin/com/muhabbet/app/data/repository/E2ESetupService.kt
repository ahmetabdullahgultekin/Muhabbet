package com.muhabbet.app.data.repository

import com.muhabbet.shared.dto.PreKeyDto
import com.muhabbet.shared.port.E2EKeyManager

/**
 * Handles E2E encryption key setup after login/registration.
 *
 * Flow:
 * 1. Generate identity key pair (X25519)
 * 2. Generate signed pre-key
 * 3. Register key bundle on server
 * 4. Generate and upload one-time pre-keys (OTPKs)
 *
 * Called once per device registration. OTPKs should be
 * replenished periodically when server count drops.
 */
class E2ESetupService(
    private val keyManager: E2EKeyManager,
    private val encryptionRepository: EncryptionRepository
) {

    /**
     * Register keys with the server, generating only if not already persisted.
     *
     * If identity key already exists in persistent storage, reuses it
     * and only re-registers the bundle with the server (idempotent).
     * New keys are only generated on first run or after key wipe.
     */
    suspend fun registerKeys() {
        try {
            // 1. Reuse existing identity key or generate new one
            val identityKey = keyManager.getIdentityPublicKey()
                ?: keyManager.generateIdentityKeyPair()

            // 2. Generate signed pre-key (rotated per session for forward secrecy)
            val (signedPreKeyId, signedPreKey) = keyManager.generateSignedPreKey()

            // 3. Register key bundle on server (idempotent — server replaces existing)
            encryptionRepository.registerKeyBundle(
                identityKey = identityKey,
                signedPreKey = signedPreKey,
                signedPreKeyId = signedPreKeyId,
                registrationId = keyManager.getRegistrationId()
            )

            // 4. Generate and upload one-time pre-keys
            val preKeys = keyManager.generateOneTimePreKeys(count = INITIAL_PREKEY_COUNT)
            val preKeyDtos = preKeys.map { (keyId, publicKey) ->
                PreKeyDto(keyId = keyId, publicKey = publicKey)
            }
            encryptionRepository.uploadPreKeys(preKeyDtos)
        } catch (_: Exception) {
            // E2E setup failure is non-fatal — messaging still works via TLS
        }
    }

    /**
     * Establish an encrypted session with a user before first message.
     * Fetches their pre-key bundle from the server and initializes
     * a Double Ratchet session.
     */
    suspend fun ensureSession(recipientId: String): Boolean {
        if (keyManager.hasSession(recipientId)) return true

        return try {
            val bundle = encryptionRepository.fetchPreKeyBundle(recipientId)
            keyManager.initializeSession(
                recipientId = recipientId,
                identityKey = bundle.identityKey,
                signedPreKey = bundle.signedPreKey,
                signedPreKeyId = bundle.signedPreKeyId,
                oneTimePreKey = bundle.oneTimePreKey,
                oneTimePreKeyId = bundle.oneTimePreKeyId
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val INITIAL_PREKEY_COUNT = 100
    }
}

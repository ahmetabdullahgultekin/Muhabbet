package com.muhabbet.app.data.repository

import com.muhabbet.app.util.Log
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
        // Refuses to publish placeholder material. NoOpKeyManager is wired on BOTH platforms while
        // libsignal is blocked, and it mints a fresh "noop-identity-key-<random>" per process
        // because it holds the key in a plain field. This method ran on every launch, so the
        // production key tables accumulated junk that is indistinguishable from real X3DH material
        // at the database level, for users who have E2E switched off.
        //
        // The caller also gates on E2EConfig.ENABLED. This second check is deliberate: the flag and
        // the key manager are set in different files, and the combination that does damage —
        // flag on, placeholder manager wired — is exactly the one a rollout would produce.
        if (!keyManager.producesRealKeyMaterial) {
            Log.e(
                TAG,
                "Refusing to register keys: ${keyManager::class.simpleName} produces placeholder " +
                    "material. Wire a real E2EKeyManager before enabling E2E."
            )
            return
        }

        // 1. Reuse existing identity key or generate new one
        val identityKey = keyManager.getIdentityPublicKey()
            ?: keyManager.generateIdentityKeyPair()

        // 2. Generate signed pre-key (rotated per session for forward secrecy)
        val (signedPreKeyId, signedPreKey, signedPreKeySignature) = keyManager.generateSignedPreKey()

        // 3. Register key bundle on server (idempotent — server replaces existing)
        encryptionRepository.registerKeyBundle(
            identityKey = identityKey,
            signedPreKey = signedPreKey,
            signedPreKeySignature = signedPreKeySignature,
            signedPreKeyId = signedPreKeyId,
            registrationId = keyManager.getRegistrationId()
        )

        // 4. Generate and upload one-time pre-keys
        val preKeys = keyManager.generateOneTimePreKeys(count = INITIAL_PREKEY_COUNT)
        val preKeyDtos = preKeys.map { (keyId, publicKey) ->
            PreKeyDto(keyId = keyId, publicKey = publicKey)
        }
        encryptionRepository.uploadPreKeys(preKeyDtos)
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
                signedPreKeySignature = bundle.signedPreKeySignature,
                signedPreKeyId = bundle.signedPreKeyId,
                oneTimePreKey = bundle.oneTimePreKey,
                oneTimePreKeyId = bundle.oneTimePreKeyId
            )
            true
        } catch (e: Exception) {
            // Absorbed on purpose: the caller (MessageEncryptor) falls back to sending the message
            // unencrypted, and E2E is flag-OFF in production anyway, so there is nothing to tell the
            // user. Logged because a silent false here is indistinguishable from a design decision.
            Log.w(TAG, "Could not establish an encrypted session with $recipientId: $e")
            false
        }
    }

    companion object {
        const val INITIAL_PREKEY_COUNT = 100
        private const val TAG = "E2ESetupService"
    }
}

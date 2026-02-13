package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.PreKeyBundleResponse
import com.muhabbet.shared.dto.PreKeyDto
import com.muhabbet.shared.dto.RegisterKeyBundleRequest
import com.muhabbet.shared.dto.UploadPreKeysRequest

/**
 * Repository for E2E encryption key management.
 * Communicates with backend key exchange endpoints (X3DH protocol).
 *
 * Flow:
 * 1. On registration/login: registerKeyBundle() + uploadPreKeys()
 * 2. Before first message to user: fetchPreKeyBundle() to establish session
 * 3. Periodically: uploadPreKeys() to replenish one-time pre-keys
 */
class EncryptionRepository(private val apiClient: ApiClient) {

    /**
     * Register identity key + signed pre-key bundle on the server.
     * Called once per device registration or key rotation.
     */
    suspend fun registerKeyBundle(
        identityKey: String,
        signedPreKey: String,
        signedPreKeyId: Int,
        registrationId: Int
    ) {
        apiClient.put<Unit>(
            "/api/v1/encryption/keys",
            RegisterKeyBundleRequest(
                identityKey = identityKey,
                signedPreKey = signedPreKey,
                signedPreKeyId = signedPreKeyId,
                registrationId = registrationId
            )
        )
    }

    /**
     * Upload one-time pre-keys (OTPKs) for X3DH.
     * Server distributes one OTPK per session establishment.
     * Should replenish when server count drops below threshold.
     */
    suspend fun uploadPreKeys(preKeys: List<PreKeyDto>) {
        apiClient.post<Unit>(
            "/api/v1/encryption/prekeys",
            UploadPreKeysRequest(preKeys = preKeys)
        )
    }

    /**
     * Fetch a target user's pre-key bundle to establish an encrypted session.
     * The server consumes one OTPK (if available) per fetch.
     */
    suspend fun fetchPreKeyBundle(targetUserId: String): PreKeyBundleResponse {
        val response = apiClient.get<PreKeyBundleResponse>(
            "/api/v1/encryption/bundle/$targetUserId"
        )
        return response.data
            ?: throw Exception("ENCRYPTION_KEY_BUNDLE_NOT_FOUND")
    }
}

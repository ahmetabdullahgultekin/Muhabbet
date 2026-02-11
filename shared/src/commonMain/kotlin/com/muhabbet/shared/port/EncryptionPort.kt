package com.muhabbet.shared.port

/**
 * Encryption port — the seam where Signal Protocol plugs in later.
 *
 * MVP: NoOpEncryption passes content through unchanged (TLS-only).
 * Phase 2: SignalEncryption implements X3DH + Double Ratchet.
 *
 * This interface is in the shared module because BOTH backend and mobile
 * need encryption/decryption capabilities.
 */
interface EncryptionPort {

    /** Encrypt content for a specific recipient */
    suspend fun encrypt(content: ByteArray, recipientId: String, deviceId: String): ByteArray

    /** Decrypt content from a specific sender */
    suspend fun decrypt(content: ByteArray, senderId: String, deviceId: String): ByteArray
}

/**
 * MVP implementation — passes content through unchanged.
 * TLS 1.3 provides transport encryption.
 */
class NoOpEncryption : EncryptionPort {
    override suspend fun encrypt(content: ByteArray, recipientId: String, deviceId: String): ByteArray = content
    override suspend fun decrypt(content: ByteArray, senderId: String, deviceId: String): ByteArray = content
}

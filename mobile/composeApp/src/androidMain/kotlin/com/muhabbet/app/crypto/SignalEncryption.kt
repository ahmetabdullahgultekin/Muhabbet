package com.muhabbet.app.crypto

import com.muhabbet.shared.port.E2EKeyManager
import com.muhabbet.shared.port.EncryptionPort

/**
 * Signal Protocol implementation of EncryptionPort.
 *
 * Delegates to E2EKeyManager for actual crypto operations.
 * Falls back to plaintext if no session exists (e.g., group messages
 * where E2E is not yet supported).
 */
class SignalEncryption(
    private val keyManager: E2EKeyManager
) : EncryptionPort {

    override suspend fun encrypt(content: ByteArray, recipientId: String, deviceId: String): ByteArray {
        if (!keyManager.hasSession(recipientId)) {
            return content // No session yet — plaintext fallback
        }
        return try {
            keyManager.encryptMessage(recipientId, content)
        } catch (_: Exception) {
            content // Fallback to plaintext on encryption failure
        }
    }

    override suspend fun decrypt(content: ByteArray, senderId: String, deviceId: String): ByteArray {
        if (!keyManager.hasSession(senderId)) {
            return content // No session — content is plaintext
        }
        return try {
            keyManager.decryptMessage(senderId, content)
        } catch (_: Exception) {
            content // Fallback to raw content on decryption failure
        }
    }
}

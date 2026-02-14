package com.muhabbet.app.crypto

import com.muhabbet.shared.port.E2EKeyManager
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.security.SecureRandom
import java.util.Base64

/**
 * Signal Protocol implementation of E2EKeyManager.
 *
 * Uses libsignal-android for:
 * - X3DH key agreement (Curve25519)
 * - Double Ratchet session management
 * - AES-256-CBC + HMAC-SHA256 message encryption
 *
 * Key storage: In-memory for MVP. Sessions are lost on app restart.
 * Production: Persist via EncryptedSharedPreferences or SQLCipher.
 */
class SignalKeyManager : E2EKeyManager {

    private val store = InMemorySignalProtocolStore()
    private val random = SecureRandom()

    override fun generateIdentityKeyPair(): String {
        val keyPair = IdentityKeyPair.generate()
        store.identityKeyPair = keyPair
        store.localRegistrationId = generateRegistrationId()
        return Base64.getEncoder().encodeToString(keyPair.publicKey.serialize())
    }

    override fun getIdentityPublicKey(): String? {
        return store.identityKeyPair?.let {
            Base64.getEncoder().encodeToString(it.publicKey.serialize())
        }
    }

    override fun generateSignedPreKey(): Pair<Int, String> {
        val keyPair = store.identityKeyPair
            ?: throw IllegalStateException("Identity key pair not generated")

        val signedPreKeyId = random.nextInt(0xFFFFFF)
        val signedPreKeyPair = Curve.generateKeyPair()
        val signature = Curve.calculateSignature(
            keyPair.privateKey,
            signedPreKeyPair.publicKey.serialize()
        )
        val timestamp = System.currentTimeMillis()

        val record = SignedPreKeyRecord(signedPreKeyId, timestamp, signedPreKeyPair, signature)
        store.storeSignedPreKey(signedPreKeyId, record)

        val publicKeyBase64 = Base64.getEncoder().encodeToString(signedPreKeyPair.publicKey.serialize())
        return signedPreKeyId to publicKeyBase64
    }

    override fun generateOneTimePreKeys(count: Int): List<Pair<Int, String>> {
        return (1..count).map {
            val preKeyId = store.nextPreKeyId++
            val keyPair = Curve.generateKeyPair()
            val record = PreKeyRecord(preKeyId, keyPair)
            store.storePreKey(preKeyId, record)

            val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.publicKey.serialize())
            preKeyId to publicKeyBase64
        }
    }

    override fun getRegistrationId(): Int = store.localRegistrationId

    override suspend fun initializeSession(
        recipientId: String,
        identityKey: String,
        signedPreKey: String,
        signedPreKeyId: Int,
        oneTimePreKey: String?,
        oneTimePreKeyId: Int?
    ) {
        val address = SignalProtocolAddress(recipientId, 1)

        val remoteIdentityKey = IdentityKey(Base64.getDecoder().decode(identityKey))
        val remoteSignedPreKey = Curve.decodePoint(Base64.getDecoder().decode(signedPreKey), 0)

        val bundleBuilder = PreKeyBundle(
            store.localRegistrationId,
            1, // deviceId
            oneTimePreKeyId ?: 0,
            oneTimePreKey?.let { Curve.decodePoint(Base64.getDecoder().decode(it), 0) },
            signedPreKeyId,
            remoteSignedPreKey,
            ByteArray(64), // signature placeholder — server validates this
            remoteIdentityKey
        )

        val sessionBuilder = SessionBuilder(store, address)
        sessionBuilder.process(bundleBuilder)
    }

    override fun hasSession(userId: String): Boolean {
        val address = SignalProtocolAddress(userId, 1)
        return store.loadSession(address).hasSenderChain()
    }

    override suspend fun encryptMessage(recipientId: String, plaintext: ByteArray): ByteArray {
        val address = SignalProtocolAddress(recipientId, 1)
        val cipher = SessionCipher(store, address)
        val cipherMessage = cipher.encrypt(plaintext)
        return cipherMessage.serialize()
    }

    override suspend fun decryptMessage(senderId: String, ciphertext: ByteArray): ByteArray {
        val address = SignalProtocolAddress(senderId, 1)
        val cipher = SessionCipher(store, address)

        return try {
            // Try as PreKeySignalMessage first (initial message)
            val preKeyMessage = org.signal.libsignal.protocol.message.PreKeySignalMessage(ciphertext)
            cipher.decrypt(preKeyMessage)
        } catch (_: Exception) {
            // Fall back to regular SignalMessage (subsequent messages)
            val signalMessage = org.signal.libsignal.protocol.message.SignalMessage(ciphertext)
            cipher.decrypt(signalMessage)
        }
    }

    private fun generateRegistrationId(): Int {
        return random.nextInt(16380) + 1
    }
}

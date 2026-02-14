package com.muhabbet.app.crypto

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of SignalProtocolStore for MVP.
 *
 * Stores all key material in memory — sessions are lost on app restart.
 * Production: Replace with SQLCipher/EncryptedSharedPreferences backend.
 */
class InMemorySignalProtocolStore : SignalProtocolStore, SenderKeyStore {

    var identityKeyPair: IdentityKeyPair? = null
    var localRegistrationId: Int = 0
    var nextPreKeyId: Int = 1

    private val preKeys = ConcurrentHashMap<Int, PreKeyRecord>()
    private val signedPreKeys = ConcurrentHashMap<Int, SignedPreKeyRecord>()
    private val sessions = ConcurrentHashMap<SignalProtocolAddress, SessionRecord>()
    private val identities = ConcurrentHashMap<SignalProtocolAddress, IdentityKey>()
    private val senderKeys = ConcurrentHashMap<String, SenderKeyRecord>()

    // ─── IdentityKeyStore ────────────────────────────

    override fun getIdentityKeyPair(): IdentityKeyPair {
        return identityKeyPair ?: throw IllegalStateException("Identity key pair not initialized")
    }

    override fun getLocalRegistrationId(): Int = localRegistrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val existing = identities.put(address, identityKey)
        return existing != null && existing != identityKey
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        val trusted = identities[address] ?: return true
        return trusted == identityKey
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        return identities[address]
    }

    // ─── PreKeyStore ────────────────────────────

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        return preKeys[preKeyId]
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No such pre-key: $preKeyId")
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeys[preKeyId] = record
    }

    override fun containsPreKey(preKeyId: Int): Boolean = preKeys.containsKey(preKeyId)

    override fun removePreKey(preKeyId: Int) {
        preKeys.remove(preKeyId)
    }

    // ─── SignedPreKeyStore ────────────────────────────

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        return signedPreKeys[signedPreKeyId]
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No such signed pre-key: $signedPreKeyId")
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = signedPreKeys.values.toList()

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeys[signedPreKeyId] = record
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        signedPreKeys.containsKey(signedPreKeyId)

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeys.remove(signedPreKeyId)
    }

    // ─── SessionStore ────────────────────────────

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        return sessions[address] ?: SessionRecord()
    }

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> {
        return addresses.map { loadSession(it) }
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        return sessions.keys
            .filter { it.name == name && it.deviceId != 1 }
            .map { it.deviceId }
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessions[address] = record
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        sessions.containsKey(address)

    override fun deleteSession(address: SignalProtocolAddress) {
        sessions.remove(address)
    }

    override fun deleteAllSessions(name: String) {
        sessions.keys.filter { it.name == name }.forEach { sessions.remove(it) }
    }

    // ─── SenderKeyStore ────────────────────────────

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
        senderKeys["${sender.name}::${sender.deviceId}::$distributionId"] = record
    }

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord {
        return senderKeys["${sender.name}::${sender.deviceId}::$distributionId"] ?: SenderKeyRecord()
    }
}

package com.muhabbet.app.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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
import java.util.Base64
import java.util.UUID

/**
 * Persistent implementation of SignalProtocolStore using EncryptedSharedPreferences.
 *
 * Identity key pair + registration ID stored in encrypted prefs.
 * Pre-keys, signed pre-keys, sessions, sender keys stored as Base64
 * serialized records in a separate encrypted prefs file.
 *
 * Replaces InMemorySignalProtocolStore for production use.
 */
class PersistentSignalProtocolStore(context: Context) : SignalProtocolStore, SenderKeyStore {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    /** Stores identity key pair, registration ID, next pre-key ID */
    private val identityPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        "muhabbet_signal_identity",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Stores sessions, pre-keys, signed pre-keys, sender keys, trusted identities */
    private val keyStorePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        "muhabbet_signal_keystore",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var identityKeyPair: IdentityKeyPair?
        get() {
            val serialized = identityPrefs.getString("identity_key_pair", null) ?: return null
            return IdentityKeyPair(Base64.getDecoder().decode(serialized))
        }
        set(value) {
            if (value != null) {
                identityPrefs.edit()
                    .putString("identity_key_pair", Base64.getEncoder().encodeToString(value.serialize()))
                    .apply()
            } else {
                identityPrefs.edit().remove("identity_key_pair").apply()
            }
        }

    var localRegistrationId: Int
        get() = identityPrefs.getInt("registration_id", 0)
        set(value) { identityPrefs.edit().putInt("registration_id", value).apply() }

    var nextPreKeyId: Int
        get() = identityPrefs.getInt("next_pre_key_id", 1)
        set(value) { identityPrefs.edit().putInt("next_pre_key_id", value).apply() }

    // ─── IdentityKeyStore ────────────────────────────

    override fun getIdentityKeyPair(): IdentityKeyPair {
        return identityKeyPair ?: throw IllegalStateException("Identity key pair not initialized")
    }

    override fun getLocalRegistrationId(): Int = localRegistrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val key = "identity:${address.name}:${address.deviceId}"
        val existing = keyStorePrefs.getString(key, null)?.let {
            IdentityKey(Base64.getDecoder().decode(it))
        }
        keyStorePrefs.edit()
            .putString(key, Base64.getEncoder().encodeToString(identityKey.serialize()))
            .apply()
        return existing != null && existing != identityKey
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        val key = "identity:${address.name}:${address.deviceId}"
        val trusted = keyStorePrefs.getString(key, null)?.let {
            IdentityKey(Base64.getDecoder().decode(it))
        } ?: return true
        return trusted == identityKey
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val key = "identity:${address.name}:${address.deviceId}"
        return keyStorePrefs.getString(key, null)?.let {
            IdentityKey(Base64.getDecoder().decode(it))
        }
    }

    // ─── PreKeyStore ────────────────────────────

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val key = "prekey:$preKeyId"
        val serialized = keyStorePrefs.getString(key, null)
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No such pre-key: $preKeyId")
        return PreKeyRecord(Base64.getDecoder().decode(serialized))
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        keyStorePrefs.edit()
            .putString("prekey:$preKeyId", Base64.getEncoder().encodeToString(record.serialize()))
            .apply()
    }

    override fun containsPreKey(preKeyId: Int): Boolean =
        keyStorePrefs.contains("prekey:$preKeyId")

    override fun removePreKey(preKeyId: Int) {
        keyStorePrefs.edit().remove("prekey:$preKeyId").apply()
    }

    // ─── SignedPreKeyStore ────────────────────────────

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val key = "signed_prekey:$signedPreKeyId"
        val serialized = keyStorePrefs.getString(key, null)
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No such signed pre-key: $signedPreKeyId")
        return SignedPreKeyRecord(Base64.getDecoder().decode(serialized))
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return keyStorePrefs.all.entries
            .filter { it.key.startsWith("signed_prekey:") }
            .mapNotNull { (_, value) ->
                (value as? String)?.let { SignedPreKeyRecord(Base64.getDecoder().decode(it)) }
            }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        keyStorePrefs.edit()
            .putString("signed_prekey:$signedPreKeyId", Base64.getEncoder().encodeToString(record.serialize()))
            .apply()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        keyStorePrefs.contains("signed_prekey:$signedPreKeyId")

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        keyStorePrefs.edit().remove("signed_prekey:$signedPreKeyId").apply()
    }

    // ─── SessionStore ────────────────────────────

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val key = "session:${address.name}:${address.deviceId}"
        val serialized = keyStorePrefs.getString(key, null)
            ?: return SessionRecord()
        return SessionRecord(Base64.getDecoder().decode(serialized))
    }

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> {
        return addresses.map { loadSession(it) }
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        return keyStorePrefs.all.keys
            .filter { it.startsWith("session:$name:") }
            .mapNotNull { it.substringAfterLast(":").toIntOrNull() }
            .filter { it != 1 }
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        keyStorePrefs.edit()
            .putString("session:${address.name}:${address.deviceId}", Base64.getEncoder().encodeToString(record.serialize()))
            .apply()
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        keyStorePrefs.contains("session:${address.name}:${address.deviceId}")

    override fun deleteSession(address: SignalProtocolAddress) {
        keyStorePrefs.edit().remove("session:${address.name}:${address.deviceId}").apply()
    }

    override fun deleteAllSessions(name: String) {
        val editor = keyStorePrefs.edit()
        keyStorePrefs.all.keys
            .filter { it.startsWith("session:$name:") }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    // ─── SenderKeyStore ────────────────────────────

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
        val key = "senderkey:${sender.name}::${sender.deviceId}::$distributionId"
        keyStorePrefs.edit()
            .putString(key, Base64.getEncoder().encodeToString(record.serialize()))
            .apply()
    }

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord {
        val key = "senderkey:${sender.name}::${sender.deviceId}::$distributionId"
        val serialized = keyStorePrefs.getString(key, null)
            ?: return SenderKeyRecord()
        return SenderKeyRecord(Base64.getDecoder().decode(serialized))
    }
}

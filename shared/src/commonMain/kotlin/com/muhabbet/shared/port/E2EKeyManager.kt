package com.muhabbet.shared.port

/**
 * E2E encryption key management interface.
 *
 * This defines the contract for Signal Protocol key operations.
 * MVP: InMemoryKeyManager stores keys in memory (no persistence).
 * Production: Signal Protocol implementation with secure storage.
 *
 * X3DH (Extended Triple Diffie-Hellman) key agreement:
 * - Identity Key (IK): Long-term key pair, persisted permanently
 * - Signed Pre-Key (SPK): Medium-term, rotated periodically
 * - One-Time Pre-Keys (OTPKs): Single-use, consumed per session
 * - Ephemeral Key (EK): Generated per session initiation
 *
 * Double Ratchet session state:
 * - Root key chain → chain keys → message keys
 * - Each message has a unique key derived from the ratchet
 */
interface E2EKeyManager {

    /** Generate a new identity key pair. Returns Base64-encoded public key. */
    fun generateIdentityKeyPair(): String

    /** Get the current identity public key (Base64). */
    fun getIdentityPublicKey(): String?

    /** Generate a signed pre-key. Returns (keyId, Base64 public key). */
    fun generateSignedPreKey(): Pair<Int, String>

    /** Generate N one-time pre-keys. Returns list of (keyId, Base64 public key). */
    fun generateOneTimePreKeys(count: Int): List<Pair<Int, String>>

    /** Get the registration ID for this device. */
    fun getRegistrationId(): Int

    /**
     * Initialize a session with a remote user using their pre-key bundle.
     * This performs the X3DH key agreement and creates a Double Ratchet session.
     */
    suspend fun initializeSession(
        recipientId: String,
        identityKey: String,
        signedPreKey: String,
        signedPreKeyId: Int,
        oneTimePreKey: String?,
        oneTimePreKeyId: Int?
    )

    /** Check if we have an active session with a user. */
    fun hasSession(userId: String): Boolean

    /** Encrypt a message for a recipient. Requires active session. */
    suspend fun encryptMessage(recipientId: String, plaintext: ByteArray): ByteArray

    /** Decrypt a message from a sender. Requires active session. */
    suspend fun decryptMessage(senderId: String, ciphertext: ByteArray): ByteArray
}

/**
 * MVP key manager — passes content through unchanged.
 * All key operations return placeholder values.
 * Used until Signal Protocol library is integrated.
 */
class NoOpKeyManager : E2EKeyManager {
    private var identityKey: String? = null
    private var registrationId: Int = 1
    private var nextPreKeyId = 1
    private val sessions = mutableSetOf<String>()

    override fun generateIdentityKeyPair(): String {
        identityKey = "noop-identity-key-${System.currentTimeMillis()}"
        return identityKey!!
    }

    override fun getIdentityPublicKey(): String? = identityKey

    override fun generateSignedPreKey(): Pair<Int, String> {
        return 1 to "noop-signed-pre-key"
    }

    override fun generateOneTimePreKeys(count: Int): List<Pair<Int, String>> {
        return (1..count).map { i ->
            val id = nextPreKeyId++
            id to "noop-otpk-$id"
        }
    }

    override fun getRegistrationId(): Int = registrationId

    override suspend fun initializeSession(
        recipientId: String,
        identityKey: String,
        signedPreKey: String,
        signedPreKeyId: Int,
        oneTimePreKey: String?,
        oneTimePreKeyId: Int?
    ) {
        sessions.add(recipientId)
    }

    override fun hasSession(userId: String): Boolean = sessions.contains(userId)

    override suspend fun encryptMessage(recipientId: String, plaintext: ByteArray): ByteArray = plaintext

    override suspend fun decryptMessage(senderId: String, ciphertext: ByteArray): ByteArray = ciphertext
}

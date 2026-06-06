package com.muhabbet.shared.port

/**
 * Crypto seam for multi-device linking (Tier 2). This is the interface where the **libsignal**
 * per-device session transfer plugs in later — it is deliberately defined NOW, with a
 * [NotYetImplementedDeviceLinkCrypto] stub, so the non-crypto registry + transport scaffolding can
 * ship and be tested without faking any encryption.
 *
 * ## Why this is a stub today (the blocked boundary)
 * Real linking requires the companion to establish its **own** Signal identity and run X3DH against
 * the primary so each device has an independent Double-Ratchet session (no key material is ever
 * copied between devices). That work is **BLOCKED** on the libsignal upgrade:
 *  - Signal stopped publishing `libsignal-android` to Maven Central (frozen at `0.86.5`); current
 *    versions are only on Signal's own Maven repo.
 *  - The Android Signal code targets a ≤0.70-era API and does not compile against any current pin
 *    (Curve removed, IdentityKeyStore.saveIdentity contract changed, PreKeyBundle Kyber form,
 *    SessionCipher localAddress arg). See CLAUDE.md "libsignal upgrade (BLOCKED)".
 *
 * Until that rewrite lands and is two-device round-trip verified on a real Android build, every
 * method here throws [NotYetImplementedException]. The data model (device registry) and transport
 * (QR handshake / linked-device management) do NOT depend on these methods succeeding, so they are
 * fully functional behind the `multiDevice.enabled` flag while the crypto stays a no-op boundary.
 *
 * DO NOT replace the stub with home-grown encryption — "do not guess crypto". The only valid
 * implementation is one backed by a compiled, verified libsignal.
 */
interface DeviceLinkCrypto {

    /**
     * Produce this device's PUBLIC prekey bundle to embed in the QR / link-complete request.
     * Returns an opaque, server-storable blob — never private key material.
     */
    fun exportPublicBundle(): String

    /**
     * Establish a per-device Signal session against the peer's [peerPublicBundle] (X3DH on link).
     * The companion runs this against the primary's bundle and vice-versa.
     */
    fun establishSession(peerDeviceId: String, peerPublicBundle: String)

    /**
     * Tear down the local session to a revoked/removed device so its old ciphertext is no longer
     * decryptable (forward secrecy on revoke).
     */
    fun dropSession(peerDeviceId: String)
}

/** Thrown by [NotYetImplementedDeviceLinkCrypto] to make the blocked boundary explicit and loud. */
class NotYetImplementedException(message: String) : RuntimeException(message)

/**
 * The shipped default. Every method throws [NotYetImplementedException] with a pointer to the
 * libsignal block. This is intentional: it lets the registry + transport slices run and be tested
 * end-to-end while making it impossible to silently ship a fake-crypto link.
 */
class NotYetImplementedDeviceLinkCrypto : DeviceLinkCrypto {

    private fun blocked(op: String): Nothing = throw NotYetImplementedException(
        "DeviceLinkCrypto.$op is not yet implemented: per-device Signal session transfer is " +
            "BLOCKED on the libsignal upgrade (frozen at 0.86.5; androidMain targets a ≤0.70 API). " +
            "See CLAUDE.md 'libsignal upgrade (BLOCKED)' and " +
            "docs/design/T2-multi-device-linked-sessions.md. Do not substitute home-grown crypto."
    )

    override fun exportPublicBundle(): String = blocked("exportPublicBundle")
    override fun establishSession(peerDeviceId: String, peerPublicBundle: String) = blocked("establishSession")
    override fun dropSession(peerDeviceId: String) = blocked("dropSession")
}

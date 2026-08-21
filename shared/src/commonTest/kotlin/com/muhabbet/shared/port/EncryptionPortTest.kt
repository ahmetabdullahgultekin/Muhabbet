package com.muhabbet.shared.port

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the MVP NoOp key manager (non-suspending surface).
 *
 * IMPORTANT: NoOp is plaintext-passthrough by design (TLS-only MVP). These tests pin that the seam
 * is *transparent and total* — they assert NO security property. The one security-relevant
 * assertion is the opposite one: that [NoOpKeyManager.producesRealKeyMaterial] stays `false`, so a
 * caller that publishes key material to a server can refuse. That flag is the guard added after
 * `registerKeys()` was found uploading `noop-identity-key-<random>` into the production key tables
 * on every launch, where it is indistinguishable from real X3DH material.
 */
class EncryptionPortTest {

    @Test
    fun noop_key_manager_declares_its_material_fake() {
        // Fail-closed guard: nothing this manager returns may be published to a server.
        assertFalse(NoOpKeyManager().producesRealKeyMaterial)
    }

    @Test
    fun identityKey_is_null_until_generated_then_stable() {
        val km = NoOpKeyManager()
        assertNull(km.getIdentityPublicKey(), "no identity before generate")
        val generated = km.generateIdentityKeyPair()
        assertTrue(generated.startsWith("noop-identity-key-"))
        // getIdentityPublicKey must hand back exactly what generate returned — the placeholder is
        // random per call, so a getter that regenerated would change a caller's published key.
        assertEquals(generated, km.getIdentityPublicKey())
    }

    @Test
    fun preKeys_have_distinct_increasing_ids() {
        val km = NoOpKeyManager()
        val keys = km.generateOneTimePreKeys(3)
        assertEquals(3, keys.size)
        val ids = keys.map { it.first }
        assertEquals(ids.distinct(), ids, "prekey ids must be unique")
        assertEquals(ids.sorted(), ids, "prekey ids must increase")
    }

    @Test
    fun preKey_ids_keep_increasing_across_calls() {
        val km = NoOpKeyManager()
        val first = km.generateOneTimePreKeys(2).map { it.first }
        val second = km.generateOneTimePreKeys(2).map { it.first }
        assertEquals(4, (first + second).distinct().size, "ids must not restart between batches")
    }

    @Test
    fun signedPreKey_returns_triple() {
        val km = NoOpKeyManager()
        val (id, pub, sig) = km.generateSignedPreKey()
        assertEquals(1, id)
        assertNotNull(pub)
        assertNotNull(sig)
    }

    @Test
    fun registrationId_is_stable() {
        val km = NoOpKeyManager()
        assertEquals(km.getRegistrationId(), km.getRegistrationId())
    }

    @Test
    fun hasSession_is_false_until_a_session_is_initialised() {
        val km = NoOpKeyManager()
        assertFalse(km.hasSession("user-1"))
    }
}

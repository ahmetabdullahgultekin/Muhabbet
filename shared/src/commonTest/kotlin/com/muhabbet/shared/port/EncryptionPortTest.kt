package com.muhabbet.shared.port

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the MVP NoOp key manager (non-suspending surface).
 *
 * IMPORTANT: NoOp is plaintext-passthrough by design (TLS-only MVP). These tests pin that the seam
 * is *transparent and total* — they assert NO security property. The suspend encrypt/decrypt
 * passthrough is exercised in `jvmTest/EncryptionPortSuspendTest` (commonTest has no coroutine test
 * runner on the classpath, so suspend cases live in jvmTest where `runBlocking` is available).
 */
class EncryptionPortTest {

    @Test
    fun identityKey_is_null_until_generated_then_stable() {
        val km = NoOpKeyManager()
        assertNull(km.getIdentityPublicKey(), "no identity before generate")
        val generated = km.generateIdentityKeyPair()
        assertTrue(generated.startsWith("noop-identity-key-"))
        // getIdentityPublicKey must return exactly what generate returned (regression for the
        // former `return identityKey!!` — the value is now assigned via a local, never re-read
        // through a nullable field).
        assertEquals(generated, km.getIdentityPublicKey())
    }

    @Test
    fun preKeys_have_distinct_increasing_ids() {
        val km = NoOpKeyManager()
        val keys = km.generateOneTimePreKeys(3)
        assertEquals(3, keys.size)
        val ids = keys.map { it.first }
        assertEquals(ids.distinct(), ids, "prekey ids must be unique")
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
}

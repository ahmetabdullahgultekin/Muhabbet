package com.muhabbet.shared.port

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-side tests for the suspend surface of the NoOp crypto seam. Lives in jvmTest because
 * `runBlocking` is JVM-available and commonTest has no coroutine test runner on the classpath.
 *
 * Pins that NoOpEncryption is an identity passthrough (plaintext-under-TLS MVP) — asserts no
 * security property, only that the seam is total and transparent so the system runs flag-OFF.
 */
class EncryptionPortSuspendTest {

    @Test
    fun noop_encrypt_is_identity_passthrough() = runBlocking {
        val port: EncryptionPort = NoOpEncryption()
        val plain = byteArrayOf(1, 2, 3, 4, 5)
        val out = port.encrypt(plain, recipientId = "u2", deviceId = "d1")
        assertContentEquals(plain, out)
    }

    @Test
    fun noop_decrypt_round_trips() = runBlocking {
        val port: EncryptionPort = NoOpEncryption()
        val plain = "Merhaba".encodeToByteArray()
        val enc = port.encrypt(plain, "u2", "d1")
        val dec = port.decrypt(enc, "u1", "d1")
        assertContentEquals(plain, dec)
    }

    @Test
    fun noop_keymanager_tracks_session_after_init() = runBlocking {
        val km = NoOpKeyManager()
        assertEquals(false, km.hasSession("u2"))
        km.initializeSession("u2", "ik", "spk", null, 1, null, null)
        assertTrue(km.hasSession("u2"))
    }
}

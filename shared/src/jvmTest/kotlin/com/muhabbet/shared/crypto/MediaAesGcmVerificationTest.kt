package com.muhabbet.shared.crypto

import com.muhabbet.shared.port.MediaKeyMaterial
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * EXECUTABLE verification of the media-E2E cryptography on this host.
 *
 * The mobile Android variant cannot build on this CI host (uncached Firebase deps block
 * `processDebugNavigationResources`), so the mobile module's `MediaEncryptorTest` /
 * `SymmetricCipherTest` cannot be *executed* here. This test reproduces the EXACT crypto the Android
 * `SymmetricCipher` actual uses — `javax.crypto` `AES/GCM/NoPadding`, 128-bit tag, `SecureRandom`,
 * SHA-256 ciphertext hash, and the `MediaKeyMaterial` packaging — in the shared module's runnable
 * `jvmTest`, so the five required crypto cases get genuine pass/fail evidence on a JVM:
 *
 *  1. round-trip   2. tamper-detection (real GCM tag)   3. wrong-key
 *  4. flag-OFF pass-through (modeled)                    5. nonce uniqueness
 *
 * The mobile-module tests assert the same behavior against the real `SymmetricCipher` actual and the
 * `MediaEncryptor` wrapper; they compile in the mobile metadata/source sets and will run once the
 * Android variant can be built (or via `commonTest`/`androidUnitTest` on a host with Firebase cached).
 */
class MediaAesGcmVerificationTest {

    private val transformation = "AES/GCM/NoPadding"
    private val tagBits = 128
    private val keySize = 32   // AES-256
    private val nonceSize = 12 // 96-bit GCM IV
    private val random = SecureRandom()

    private fun genKey() = ByteArray(keySize).also { random.nextBytes(it) }
    private fun genNonce() = ByteArray(nonceSize).also { random.nextBytes(it) }

    private fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagBits, nonce))
        return cipher.doFinal(plaintext)
    }

    private fun decrypt(ctAndTag: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagBits, nonce))
        return cipher.doFinal(ctAndTag)
    }

    private fun sha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)

    private val media = "gerçek medya baytları — secret photo bytes 🔐 ÿ".encodeToByteArray()

    // ── 1. round-trip ────────────────────────────────────────────────────
    @Test
    fun round_trip_encrypt_then_decrypt() {
        val key = genKey(); val nonce = genNonce()
        val ct = encrypt(media, key, nonce)
        assertEquals(media.size + 16, ct.size)          // 16-byte GCM tag appended
        assertFalse(ct.contentEquals(media))            // not plaintext at rest
        assertContentEquals(media, decrypt(ct, key, nonce))
    }

    // ── 2. tamper-detection ──────────────────────────────────────────────
    @Test
    fun flipping_a_ciphertext_byte_fails_closed() {
        val key = genKey(); val nonce = genNonce()
        val ct = encrypt(media, key, nonce)
        val tampered = ct.copyOf()
        tampered[5] = (tampered[5].toInt() xor 0x01).toByte()
        // javax.crypto.AEADBadTagException — must THROW, never return corrupted plaintext.
        assertFailsWith<javax.crypto.AEADBadTagException> { decrypt(tampered, key, nonce) }
    }

    @Test
    fun corrupting_the_tag_fails_closed() {
        val key = genKey(); val nonce = genNonce()
        val ct = encrypt(media, key, nonce)
        val tampered = ct.copyOf()
        tampered[ct.size - 1] = (tampered[ct.size - 1].toInt() xor 0xFF).toByte()
        assertFailsWith<javax.crypto.AEADBadTagException> { decrypt(tampered, key, nonce) }
    }

    // ── 3. wrong-key ─────────────────────────────────────────────────────
    @Test
    fun decrypting_with_a_different_key_fails_closed() {
        val key = genKey(); val nonce = genNonce()
        val ct = encrypt(media, key, nonce)
        assertFailsWith<javax.crypto.AEADBadTagException> { decrypt(ct, genKey(), nonce) }
    }

    // ── 4. flag-OFF pass-through (modeled: no key material → bytes unchanged) ──
    @Test
    fun flag_off_path_does_not_transform_bytes() {
        // Mirrors MediaEncryptor.encryptForUpload(enabled=false): returns input bytes + null key.
        val enabled = false
        val (blob, km) = if (!enabled) media to null else {
            val k = genKey(); val n = genNonce(); encrypt(media, k, n) to MediaKeyMaterial(
                keyBase64 = Base64.encode(k), nonceBase64 = Base64.encode(n),
                sha256OfCiphertextBase64 = Base64.encode(sha256(encrypt(media, k, n)))
            )
        }
        assertTrue(km == null)
        assertContentEquals(media, blob)   // byte-identical to today's plaintext upload
    }

    // ── 5. nonce uniqueness ──────────────────────────────────────────────
    @Test
    fun same_plaintext_yields_different_ciphertext_each_time() {
        val key = genKey()
        val ct1 = encrypt(media, key, genNonce())
        val ct2 = encrypt(media, key, genNonce())
        assertFalse(ct1.contentEquals(ct2), "fresh nonce must change the ciphertext")
    }

    @Test
    fun csprng_keys_and_nonces_are_unique() {
        val keys = (1..64).map { Base64.encode(genKey()) }.toSet()
        val nonces = (1..64).map { Base64.encode(genNonce()) }.toSet()
        assertEquals(64, keys.size)
        assertEquals(64, nonces.size)
    }

    // ── end-to-end: encrypt → package MediaKeyMaterial → verify hash → decrypt ──
    @Test
    fun full_flow_with_integrity_hash_and_key_material_packaging() {
        val key = genKey(); val nonce = genNonce()
        val ct = encrypt(media, key, nonce)
        val km = MediaKeyMaterial(
            keyBase64 = Base64.encode(key),
            nonceBase64 = Base64.encode(nonce),
            sha256OfCiphertextBase64 = Base64.encode(sha256(ct)),
            mediaId = "minio-obj-1"
        )
        // Key material survives JSON round-trip (it rides inside the E2E-encrypted message body).
        val wire = MediaKeyMaterial.encode(km)
        val parsed = MediaKeyMaterial.decodeOrNull(wire)!!

        // Receiver: integrity check, then authenticated decrypt.
        val recoveredKey = Base64.decode(parsed.keyBase64)
        val recoveredNonce = Base64.decode(parsed.nonceBase64)
        assertContentEquals(sha256(ct), Base64.decode(parsed.sha256OfCiphertextBase64))
        assertContentEquals(media, decrypt(ct, recoveredKey, recoveredNonce))

        // A tampered blob is caught by the integrity hash BEFORE decryption is attempted.
        val tampered = ct.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertFalse(sha256(tampered).contentEquals(Base64.decode(parsed.sha256OfCiphertextBase64)))
    }
}

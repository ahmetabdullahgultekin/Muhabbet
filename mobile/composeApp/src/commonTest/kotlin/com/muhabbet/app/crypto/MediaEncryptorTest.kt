package com.muhabbet.app.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Logic tests for [MediaEncryptor] — the media-blob E2E wiring (Tier 1.4).
 *
 * These run on every platform (commonTest) using a deterministic in-test
 * [SymmetricCipherGateway] that models an AUTHENTICATED cipher: it appends a keyed "tag" derived
 * from key+nonce+plaintext and rejects decryption when the tag, key, or nonce doesn't match — the
 * same fail-closed contract AES-GCM provides. The *real* AES-256-GCM behavior (genuine GCM auth
 * tag, JCE) is additionally proven in `androidUnitTest/.../SymmetricCipherTest.kt`, which executes
 * `javax.crypto` on the JVM.
 *
 * Covered (the five required cases):
 *  1. round-trip   — encrypt→decrypt returns the original bytes
 *  2. tamper       — flipping a ciphertext byte makes decrypt FAIL (throws), not corrupt plaintext
 *  3. wrong-key    — decrypt with a different key fails
 *  4. flag-OFF     — with the flag false the path does NOT transform the bytes (plaintext upload)
 *  5. nonce-unique — two encryptions of the same bytes produce different ciphertexts
 */
class MediaEncryptorTest {

    /**
     * An authenticated fake cipher. Layout: `[12B nonce][N plaintext][8B tag]`, where the tag is a
     * keyed checksum over key+nonce+plaintext. decrypt() recomputes the tag from the supplied key
     * and the embedded nonce and throws if it doesn't match — modeling AES-GCM's fail-closed auth.
     * A monotonic counter guarantees fresh, unique keys/nonces per call (CSPRNG stand-in).
     */
    private class FakeAuthCipher : SymmetricCipherGateway {
        private var counter = 0
        val keySize = 32
        val nonceSize = 12

        override fun generateKey(): ByteArray =
            ByteArray(keySize) { ((counter++ * 7 + it * 31 + 1) and 0xFF).toByte() }

        override fun generateNonce(): ByteArray =
            ByteArray(nonceSize) { ((counter++ * 13 + it * 17 + 3) and 0xFF).toByte() }

        private fun tag(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
            var h = 0x9E3779B97F4A7C15uL
            for (b in key) h = (h xor (b.toInt() and 0xFF).toULong()) * 0x100000001B3uL
            for (b in nonce) h = (h xor (b.toInt() and 0xFF).toULong()) * 0x100000001B3uL
            for (b in plaintext) h = (h xor (b.toInt() and 0xFF).toULong()) * 0x100000001B3uL
            return ByteArray(8) { ((h shr (it * 8)).toInt() and 0xFF).toByte() }
        }

        override fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
            require(key.size == keySize && nonce.size == nonceSize)
            // ciphertext = plaintext xor keystream (key[i % key.size] xor nonce[i % nonce.size])
            val ct = ByteArray(plaintext.size) {
                (plaintext[it].toInt() xor key[it % key.size].toInt() xor nonce[it % nonce.size].toInt()).toByte()
            }
            return nonce + ct + tag(key, nonce, plaintext)
        }

        override fun decrypt(ciphertextAndTag: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
            require(key.size == keySize && nonce.size == nonceSize)
            require(ciphertextAndTag.size >= nonceSize + 8) { "too short" }
            val embeddedNonce = ciphertextAndTag.copyOfRange(0, nonceSize)
            val ct = ciphertextAndTag.copyOfRange(nonceSize, ciphertextAndTag.size - 8)
            val embeddedTag = ciphertextAndTag.copyOfRange(ciphertextAndTag.size - 8, ciphertextAndTag.size)
            val pt = ByteArray(ct.size) {
                (ct[it].toInt() xor key[it % key.size].toInt() xor embeddedNonce[it % nonceSize].toInt()).toByte()
            }
            val expected = tag(key, embeddedNonce, pt)
            if (!expected.contentEquals(embeddedTag)) error("AUTH_TAG_MISMATCH") // fail closed
            return pt
        }

        override fun sha256(data: ByteArray): ByteArray {
            // Deterministic 32-byte digest that changes with any input byte (FNV-ish, not crypto).
            val out = ByteArray(32)
            var h = 0xCBF29CE484222325uL
            for (b in data) {
                h = (h xor (b.toInt() and 0xFF).toULong()) * 0x100000001B3uL
                out[(h % 32uL).toInt()] = (out[(h % 32uL).toInt()].toInt() xor (h.toInt() and 0xFF)).toByte()
            }
            for (i in 0 until 8) out[i] = ((h shr (i * 8)).toInt() and 0xFF).toByte()
            return out
        }
    }

    private val sample = "gizli medya baytları — secret media bytes 🔐".encodeToByteArray()

    // ── 1. round-trip ────────────────────────────────────────────────────
    @Test
    fun should_round_trip_encrypt_then_decrypt_back_to_original() {
        val cipher = FakeAuthCipher()
        val enc = MediaEncryptor(cipher = cipher, enabled = true)

        val encrypted = enc.encryptForUpload(sample, mediaId = "media-1")
        assertTrue(encrypted.isEncrypted, "key material must be produced when enabled")
        val km = assertNotNull(encrypted.keyMaterial)
        assertEquals("media-1", km.mediaId)
        // The uploaded blob must NOT be the plaintext.
        assertFalse(encrypted.blob.contentEquals(sample))

        val decrypted = enc.decryptDownloaded(encrypted.blob, km)
        assertContentEquals(sample, decrypted)
    }

    // ── 2. tamper-detection ──────────────────────────────────────────────
    @Test
    fun should_fail_when_ciphertext_byte_is_flipped() {
        val cipher = FakeAuthCipher()
        val enc = MediaEncryptor(cipher = cipher, enabled = true)
        val encrypted = enc.encryptForUpload(sample)
        val km = assertNotNull(encrypted.keyMaterial)

        // Flip a byte in the middle of the blob (a ciphertext byte, not the integrity-hash path).
        val tampered = encrypted.blob.copyOf()
        val idx = tampered.size / 2
        tampered[idx] = (tampered[idx].toInt() xor 0x01).toByte()

        // Must FAIL CLOSED — throw, never return corrupted plaintext.
        val ex = assertFailsWith<MediaDecryptException> { enc.decryptDownloaded(tampered, km) }
        // Integrity hash catches the at-rest change before auth even runs.
        assertEquals("MEDIA_INTEGRITY_MISMATCH", ex.message)
    }

    @Test
    fun should_fail_when_blob_passes_integrity_but_auth_tag_is_wrong() {
        // Defence-in-depth: even if an attacker could recompute a matching sha256 (modeled here by
        // handing decrypt a blob whose hash we DON'T check by going one layer down), the AES-GCM
        // auth tag still rejects it. We assert decrypt() throws on a key-mismatched blob.
        val cipher = FakeAuthCipher()
        val key = cipher.generateKey()
        val nonce = cipher.generateNonce()
        val blob = cipher.encrypt(sample, key, nonce)
        val tampered = blob.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte() // corrupt tag
        assertFailsWith<IllegalStateException> { cipher.decrypt(tampered, key, nonce) }
    }

    // ── 3. wrong-key ─────────────────────────────────────────────────────
    @Test
    fun should_fail_when_decrypting_with_a_different_key() {
        val cipher = FakeAuthCipher()
        val enc = MediaEncryptor(cipher = cipher, enabled = true)
        val encrypted = enc.encryptForUpload(sample)
        val km = assertNotNull(encrypted.keyMaterial)

        // Replace the key with a different (valid-length) key; integrity hash still matches the blob,
        // so this exercises the authenticated-decrypt failure path, not the hash path.
        val otherKey = ByteArray(32) { 0x42 }
        val wrongKm = km.copy(keyBase64 = kotlin.io.encoding.Base64.Default.encode(otherKey))

        assertFailsWith<MediaDecryptException> { enc.decryptDownloaded(encrypted.blob, wrongKm) }
    }

    // ── 4. flag-OFF pass-through ─────────────────────────────────────────
    @Test
    fun should_not_transform_bytes_when_flag_off() {
        val cipher = FakeAuthCipher()
        val enc = MediaEncryptor(cipher = cipher, enabled = false)

        val result = enc.encryptForUpload(sample)
        assertFalse(result.isEncrypted, "no key material when disabled")
        assertNull(result.keyMaterial)
        // Byte-identical to input — plaintext upload, exactly like today.
        assertContentEquals(sample, result.blob)
        assertTrue(result.blob === sample, "must return the SAME array reference, no copy")

        // Download with no key material is a pass-through too.
        assertContentEquals(sample, enc.decryptDownloaded(sample, null))
    }

    @Test
    fun default_constructor_reads_the_global_flag_which_is_OFF() {
        // The production default uses E2EConfig.mediaEncryptionActive — currently OFF.
        // So a default-constructed MediaEncryptor must NOT transform bytes (byte-identical to prod).
        assertFalse(E2EConfig.mediaEncryptionActive, "media E2E flag must ship OFF")
        val enc = MediaEncryptor() // default enabled = E2EConfig.mediaEncryptionActive
        val result = enc.encryptForUpload(sample)
        assertFalse(result.isEncrypted)
        assertContentEquals(sample, result.blob)
    }

    // ── 5. nonce uniqueness ──────────────────────────────────────────────
    @Test
    fun should_produce_different_ciphertext_for_same_input_each_time() {
        val cipher = FakeAuthCipher()
        val enc = MediaEncryptor(cipher = cipher, enabled = true)

        val a = enc.encryptForUpload(sample)
        val b = enc.encryptForUpload(sample)
        // Fresh key + fresh nonce per media → different ciphertext blobs for identical plaintext.
        assertFalse(a.blob.contentEquals(b.blob), "same plaintext must not yield identical ciphertext")
        // And the key material (key/nonce) differs too.
        assertNotNull(a.keyMaterial); assertNotNull(b.keyMaterial)
        assertFalse(a.keyMaterial!!.keyBase64 == b.keyMaterial!!.keyBase64, "key must be fresh per media")
        assertFalse(a.keyMaterial!!.nonceBase64 == b.keyMaterial!!.nonceBase64, "nonce must be fresh per media")

        // Both still round-trip independently.
        assertContentEquals(sample, enc.decryptDownloaded(a.blob, a.keyMaterial))
        assertContentEquals(sample, enc.decryptDownloaded(b.blob, b.keyMaterial))
    }

    // ── graceful fallback ────────────────────────────────────────────────
    @Test
    fun should_fall_back_to_plaintext_when_crypto_throws() {
        // A gateway that throws on encrypt (models iOS NoOp) must NOT crash the send: the helper
        // returns the plaintext bytes + null key material so the caller uploads plaintext.
        val throwing = object : SymmetricCipherGateway {
            override fun generateKey() = throw NotImplementedError("ios noop")
            override fun generateNonce() = throw NotImplementedError("ios noop")
            override fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray) = throw NotImplementedError()
            override fun decrypt(ciphertextAndTag: ByteArray, key: ByteArray, nonce: ByteArray) = throw NotImplementedError()
            override fun sha256(data: ByteArray) = throw NotImplementedError()
        }
        val enc = MediaEncryptor(cipher = throwing, enabled = true)
        val result = enc.encryptForUpload(sample)
        assertFalse(result.isEncrypted, "must fall back to plaintext on crypto failure")
        assertContentEquals(sample, result.blob)
    }
}

package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.OneTimePreKey
import com.muhabbet.messaging.domain.port.out.EncryptionKeyRepository
import com.muhabbet.shared.exception.BusinessException
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class EncryptionServiceTest {

    private val encryptionKeyRepository = mockk<EncryptionKeyRepository>()
    private lateinit var service: EncryptionService

    @BeforeEach
    fun setUp() {
        service = EncryptionService(encryptionKeyRepository)
    }

    @Test
    fun `should register key bundle`() {
        val userId = UUID.randomUUID()
        val bundle = EncryptionKeyBundle(
            userId = UUID.randomUUID(),
            identityKey = "identity-key-base64",
            signedPreKey = "signed-pre-key-base64",
            signedPreKeyId = 1,
            registrationId = 12345
        )

        every { encryptionKeyRepository.saveKeyBundle(userId, any()) } just Runs

        assertDoesNotThrow { service.registerKeyBundle(userId, bundle) }
        verify { encryptionKeyRepository.saveKeyBundle(userId, match { it.userId == userId }) }
    }

    @Test
    fun `should get key bundle`() {
        val userId = UUID.randomUUID()
        val bundle = EncryptionKeyBundle(
            userId = userId,
            identityKey = "identity-key",
            signedPreKey = "signed-pre-key",
            signedPreKeyId = 1,
            registrationId = 12345
        )

        every { encryptionKeyRepository.getKeyBundle(userId) } returns bundle

        val result = service.getKeyBundle(userId)
        assertNotNull(result)
        assertEquals("identity-key", result?.identityKey)
    }

    @Test
    fun `should upload pre-keys`() {
        val userId = UUID.randomUUID()
        val keys = (1..10).map {
            OneTimePreKey(userId = UUID.randomUUID(), keyId = it, publicKey = "key-$it")
        }

        every { encryptionKeyRepository.saveOneTimePreKeys(userId, any()) } just Runs

        assertDoesNotThrow { service.uploadPreKeys(userId, keys) }
        verify { encryptionKeyRepository.saveOneTimePreKeys(userId, match { it.size == 10 && it.all { k -> k.userId == userId } }) }
    }

    @Test
    fun `should throw when uploading empty pre-keys`() {
        val userId = UUID.randomUUID()
        assertThrows(BusinessException::class.java) {
            service.uploadPreKeys(userId, emptyList())
        }
    }

    @Test
    fun `should fetch pre-key bundle with one-time key`() {
        val targetUserId = UUID.randomUUID()
        val bundle = EncryptionKeyBundle(
            userId = targetUserId,
            identityKey = "identity-key",
            signedPreKey = "signed-pre-key",
            signedPreKeyId = 1,
            registrationId = 12345
        )
        val otpk = OneTimePreKey(userId = targetUserId, keyId = 42, publicKey = "one-time-key-42")

        every { encryptionKeyRepository.getKeyBundle(targetUserId) } returns bundle
        every { encryptionKeyRepository.consumeOneTimePreKey(targetUserId) } returns otpk

        val result = service.fetchPreKeyBundle(targetUserId)
        assertNotNull(result)
        assertEquals("identity-key", result?.identityKey)
        assertEquals("one-time-key-42", result?.oneTimePreKey)
        assertEquals(42, result?.oneTimePreKeyId)
    }

    @Test
    fun `should return null when no key bundle exists`() {
        val targetUserId = UUID.randomUUID()
        every { encryptionKeyRepository.getKeyBundle(targetUserId) } returns null

        val result = service.fetchPreKeyBundle(targetUserId)
        assertNull(result)
    }

    @Test
    fun `should return bundle without one-time key when none available`() {
        val targetUserId = UUID.randomUUID()
        val bundle = EncryptionKeyBundle(
            userId = targetUserId,
            identityKey = "identity-key",
            signedPreKey = "signed-pre-key",
            signedPreKeyId = 1,
            registrationId = 12345
        )

        every { encryptionKeyRepository.getKeyBundle(targetUserId) } returns bundle
        every { encryptionKeyRepository.consumeOneTimePreKey(targetUserId) } returns null

        val result = service.fetchPreKeyBundle(targetUserId)
        assertNotNull(result)
        assertNull(result?.oneTimePreKey)
    }
}

package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.OneTimePreKey
import com.muhabbet.messaging.domain.port.out.EncryptionKeyRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class EncryptionServiceTest {

    private lateinit var encryptionKeyRepository: EncryptionKeyRepository
    private lateinit var service: EncryptionService

    private val userId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()

    private fun createKeyBundle(uid: UUID = userId) = EncryptionKeyBundle(
        userId = uid,
        identityKey = "identity-key-base64",
        signedPreKey = "signed-pre-key-base64",
        signedPreKeyId = 1,
        registrationId = 12345
    )

    private fun createPreKeys(uid: UUID = userId, count: Int = 3) =
        (1..count).map { i ->
            OneTimePreKey(
                userId = uid,
                keyId = i,
                publicKey = "pre-key-$i-base64"
            )
        }

    @BeforeEach
    fun setUp() {
        encryptionKeyRepository = mockk(relaxed = true)
        service = EncryptionService(encryptionKeyRepository)
    }

    @Nested
    inner class RegisterKeyBundle {

        @Test
        fun `should save key bundle with correct userId`() {
            val bundle = createKeyBundle()
            val bundleSlot = slot<EncryptionKeyBundle>()

            service.registerKeyBundle(userId, bundle)

            verify { encryptionKeyRepository.saveKeyBundle(userId, capture(bundleSlot)) }
            assertEquals(userId, bundleSlot.captured.userId)
            assertEquals("identity-key-base64", bundleSlot.captured.identityKey)
            assertEquals("signed-pre-key-base64", bundleSlot.captured.signedPreKey)
            assertEquals(1, bundleSlot.captured.signedPreKeyId)
            assertEquals(12345, bundleSlot.captured.registrationId)
        }

        @Test
        fun `should override userId from bundle with parameter userId`() {
            val bundle = createKeyBundle(uid = UUID.randomUUID())
            val bundleSlot = slot<EncryptionKeyBundle>()

            service.registerKeyBundle(userId, bundle)

            verify { encryptionKeyRepository.saveKeyBundle(userId, capture(bundleSlot)) }
            assertEquals(userId, bundleSlot.captured.userId)
        }
    }

    @Nested
    inner class GetKeyBundle {

        @Test
        fun `should return key bundle when exists`() {
            val bundle = createKeyBundle()
            every { encryptionKeyRepository.getKeyBundle(userId) } returns bundle

            val result = service.getKeyBundle(userId)

            assertNotNull(result)
            assertEquals("identity-key-base64", result!!.identityKey)
        }

        @Test
        fun `should return null when bundle does not exist`() {
            every { encryptionKeyRepository.getKeyBundle(userId) } returns null

            val result = service.getKeyBundle(userId)

            assertNull(result)
        }
    }

    @Nested
    inner class UploadPreKeys {

        @Test
        fun `should save pre-keys with correct userId`() {
            val keys = createPreKeys()
            val keysSlot = slot<List<OneTimePreKey>>()

            service.uploadPreKeys(userId, keys)

            verify { encryptionKeyRepository.saveOneTimePreKeys(userId, capture(keysSlot)) }
            assertEquals(3, keysSlot.captured.size)
            keysSlot.captured.forEach { key ->
                assertEquals(userId, key.userId)
            }
        }

        @Test
        fun `should throw when pre-key list is empty`() {
            val exception = assertThrows<BusinessException> {
                service.uploadPreKeys(userId, emptyList())
            }

            assertEquals(ErrorCode.ENCRYPTION_INVALID_KEY_DATA, exception.errorCode)
        }

        @Test
        fun `should override userId in pre-keys with parameter userId`() {
            val otherUser = UUID.randomUUID()
            val keys = createPreKeys(uid = otherUser)
            val keysSlot = slot<List<OneTimePreKey>>()

            service.uploadPreKeys(userId, keys)

            verify { encryptionKeyRepository.saveOneTimePreKeys(userId, capture(keysSlot)) }
            keysSlot.captured.forEach { key ->
                assertEquals(userId, key.userId)
            }
        }
    }

    @Nested
    inner class FetchPreKeyBundle {

        @Test
        fun `should return full pre-key bundle with one-time key`() {
            val bundle = createKeyBundle(uid = targetUserId)
            val oneTimeKey = OneTimePreKey(
                userId = targetUserId,
                keyId = 42,
                publicKey = "one-time-key-42-base64"
            )

            every { encryptionKeyRepository.getKeyBundle(targetUserId) } returns bundle
            every { encryptionKeyRepository.consumeOneTimePreKey(targetUserId) } returns oneTimeKey

            val result = service.fetchPreKeyBundle(targetUserId)

            assertNotNull(result)
            assertEquals("identity-key-base64", result!!.identityKey)
            assertEquals("signed-pre-key-base64", result.signedPreKey)
            assertEquals(1, result.signedPreKeyId)
            assertEquals(12345, result.registrationId)
            assertEquals("one-time-key-42-base64", result.oneTimePreKey)
            assertEquals(42, result.oneTimePreKeyId)
        }

        @Test
        fun `should return bundle with null one-time key when none available`() {
            val bundle = createKeyBundle(uid = targetUserId)

            every { encryptionKeyRepository.getKeyBundle(targetUserId) } returns bundle
            every { encryptionKeyRepository.consumeOneTimePreKey(targetUserId) } returns null

            val result = service.fetchPreKeyBundle(targetUserId)

            assertNotNull(result)
            assertEquals("identity-key-base64", result!!.identityKey)
            assertNull(result.oneTimePreKey)
            assertNull(result.oneTimePreKeyId)
        }

        @Test
        fun `should return null when user has no key bundle`() {
            every { encryptionKeyRepository.getKeyBundle(targetUserId) } returns null

            val result = service.fetchPreKeyBundle(targetUserId)

            assertNull(result)
        }

        @Test
        fun `should consume one-time pre-key on fetch`() {
            val bundle = createKeyBundle(uid = targetUserId)
            every { encryptionKeyRepository.getKeyBundle(targetUserId) } returns bundle
            every { encryptionKeyRepository.consumeOneTimePreKey(targetUserId) } returns null

            service.fetchPreKeyBundle(targetUserId)

            verify(exactly = 1) { encryptionKeyRepository.consumeOneTimePreKey(targetUserId) }
        }
    }
}

package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.OneTimePreKey
import com.muhabbet.messaging.domain.port.`in`.ManageEncryptionUseCase
import com.muhabbet.messaging.domain.port.`in`.PreKeyBundle
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class EncryptionControllerTest {

    private lateinit var manageEncryptionUseCase: ManageEncryptionUseCase
    private lateinit var controller: EncryptionController

    private val userId = TestData.USER_ID_1

    @BeforeEach
    fun setUp() {
        manageEncryptionUseCase = mockk()
        controller = EncryptionController(manageEncryptionUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class RegisterKeyBundle {

        @Test
        fun `should register key bundle successfully`() {
            every { manageEncryptionUseCase.registerKeyBundle(userId, any()) } returns Unit

            val response = controller.registerKeyBundle(
                com.muhabbet.shared.dto.RegisterKeyBundleRequest(
                    identityKey = "identity-key-base64",
                    signedPreKey = "signed-prekey-base64",
                    signedPreKeyId = 1,
                    registrationId = 12345
                )
            )

            assert(response.statusCode.value() == 200)
            verify { manageEncryptionUseCase.registerKeyBundle(userId, any()) }
        }
    }

    @Nested
    inner class UploadPreKeys {

        @Test
        fun `should upload pre-keys successfully`() {
            every { manageEncryptionUseCase.uploadPreKeys(userId, any()) } returns Unit

            val response = controller.uploadPreKeys(
                com.muhabbet.shared.dto.UploadPreKeysRequest(
                    preKeys = listOf(
                        com.muhabbet.shared.dto.PreKeyDto(keyId = 1, publicKey = "key-1-base64"),
                        com.muhabbet.shared.dto.PreKeyDto(keyId = 2, publicKey = "key-2-base64")
                    )
                )
            )

            assert(response.statusCode.value() == 200)
            verify { manageEncryptionUseCase.uploadPreKeys(userId, any()) }
        }
    }

    @Nested
    inner class FetchPreKeyBundle {

        @Test
        fun `should return pre-key bundle for target user`() {
            val bundle = PreKeyBundle(
                identityKey = "target-identity-key",
                signedPreKey = "target-signed-prekey",
                signedPreKeyId = 1,
                registrationId = 54321,
                oneTimePreKey = "one-time-key",
                oneTimePreKeyId = 5
            )

            every { manageEncryptionUseCase.fetchPreKeyBundle(TestData.USER_ID_2) } returns bundle

            val response = controller.fetchPreKeyBundle(TestData.USER_ID_2)

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.identityKey == "target-identity-key")
            assert(response.body?.data?.oneTimePreKey == "one-time-key")
        }

        @Test
        fun `should throw ENCRYPTION_KEY_BUNDLE_NOT_FOUND when no bundle exists`() {
            every { manageEncryptionUseCase.fetchPreKeyBundle(TestData.USER_ID_2) } returns null

            try {
                controller.fetchPreKeyBundle(TestData.USER_ID_2)
                assert(false)
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.ENCRYPTION_KEY_BUNDLE_NOT_FOUND)
            }
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}

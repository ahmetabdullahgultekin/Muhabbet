package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.UserDataExport
import com.muhabbet.auth.domain.port.`in`.ManageUserDataUseCase
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
import java.time.Instant
import java.util.UUID

class UserDataControllerTest {

    private lateinit var manageUserDataUseCase: ManageUserDataUseCase
    private lateinit var controller: UserDataController

    private val userId = TestData.USER_ID_1

    @BeforeEach
    fun setUp() {
        manageUserDataUseCase = mockk()
        controller = UserDataController(manageUserDataUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class ExportUserData {

        @Test
        fun `should return full data export for authenticated user`() {
            val export = UserDataExport(
                userId = userId.toString(),
                phoneNumber = TestData.PHONE_1,
                displayName = "Test User",
                avatarUrl = null,
                about = "Hello!",
                messageCount = 150,
                conversationCount = 5,
                mediaCount = 23,
                joinedAt = Instant.parse("2025-01-15T10:00:00Z"),
                exportedAt = Instant.now()
            )

            every { manageUserDataUseCase.exportUserData(userId) } returns export

            val response = controller.exportUserData()

            assert(response.statusCode.value() == 200)
            val data = response.body?.data
            assert(data != null)
            assert(data?.userId == userId.toString())
            assert(data?.phoneNumber == TestData.PHONE_1)
            assert(data?.displayName == "Test User")
            assert(data?.messageCount == 150L)
            assert(data?.conversationCount == 5L)
            assert(data?.mediaCount == 23L)
        }

        @Test
        fun `should include all KVKK required fields in export`() {
            val export = UserDataExport(
                userId = userId.toString(),
                phoneNumber = TestData.PHONE_1,
                displayName = null,
                avatarUrl = "https://media.muhabbet.com/avatars/test.jpg",
                about = null,
                messageCount = 0,
                conversationCount = 0,
                mediaCount = 0,
                joinedAt = Instant.now()
            )

            every { manageUserDataUseCase.exportUserData(userId) } returns export

            val response = controller.exportUserData()
            val data = response.body?.data!!

            // KVKK Article 11: right to know personal data
            assert(data.userId.isNotBlank())
            assert(data.phoneNumber.isNotBlank())
            assert(data.joinedAt != null)
            assert(data.exportedAt != null)
        }

        @Test
        fun `should throw USER_NOT_FOUND for deleted user`() {
            every {
                manageUserDataUseCase.exportUserData(userId)
            } throws BusinessException(ErrorCode.USER_NOT_FOUND)

            try {
                controller.exportUserData()
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.USER_NOT_FOUND)
            }
        }
    }

    @Nested
    inner class RequestAccountDeletion {

        @Test
        fun `should request account deletion successfully`() {
            every { manageUserDataUseCase.requestAccountDeletion(userId) } returns Unit

            val response = controller.requestAccountDeletion()

            assert(response.statusCode.value() == 200)
            verify { manageUserDataUseCase.requestAccountDeletion(userId) }
        }

        @Test
        fun `should throw USER_ALREADY_DELETED for already deleted account`() {
            every {
                manageUserDataUseCase.requestAccountDeletion(userId)
            } throws BusinessException(ErrorCode.USER_ALREADY_DELETED)

            try {
                controller.requestAccountDeletion()
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.USER_ALREADY_DELETED)
            }
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}

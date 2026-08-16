package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.ExportedEncryptionKeySummary
import com.muhabbet.auth.domain.model.ExportedPage
import com.muhabbet.auth.domain.model.ExportedPrivacySettings
import com.muhabbet.auth.domain.model.ExportedProfile
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

    private fun sampleExport() = UserDataExport(
        exportedAt = Instant.now(),
        profile = ExportedProfile(
            userId = userId,
            phoneNumber = TestData.PHONE_1,
            displayName = "Test User",
            avatarUrl = null,
            about = "Hello!",
            joinedAt = Instant.parse("2025-01-15T10:00:00Z"),
            twoStepVerificationEnabled = false
        ),
        privacySettings = ExportedPrivacySettings(
            readReceiptsEnabled = true,
            onlineStatusVisibility = "everyone",
            aboutVisibility = "everyone"
        ),
        devices = emptyList(),
        sessions = emptyList(),
        loginApprovals = emptyList(),
        linkedDeviceSessions = emptyList(),
        contacts = emptyList(),
        discoverableByPhoneHash = true,
        conversations = emptyList(),
        messages = ExportedPage(items = emptyList(), nextCursor = null, hasMore = false, totalCount = 150),
        mediaFiles = ExportedPage(items = emptyList(), nextCursor = null, hasMore = false, totalCount = 23),
        chatWallpapers = emptyList(),
        chatFolders = emptyList(),
        messageBackups = emptyList(),
        ownedBroadcastLists = emptyList(),
        broadcastListMemberships = emptyList(),
        encryptionKeys = ExportedEncryptionKeySummary(registered = false, registeredAt = null, keyVersion = null, unusedOneTimePreKeyCount = 0)
    )

    @Nested
    inner class ExportUserData {

        @Test
        fun `should return the real data export for the authenticated user, not just counts`() {
            val export = sampleExport()
            every { manageUserDataUseCase.exportUserData(userId, null, null, 200) } returns export

            val response = controller.exportUserData(messagesCursor = null, mediaCursor = null, pageSize = 200)

            assert(response.statusCode.value() == 200)
            val data = response.body?.data
            assert(data != null)
            assert(data?.profile?.userId == userId)
            assert(data?.profile?.phoneNumber == TestData.PHONE_1)
            assert(data?.profile?.displayName == "Test User")
            // The old shape only had counts; this asserts the response now carries the real
            // collections (empty here, but present as actual lists/pages, not integers).
            assert(data?.messages?.totalCount == 150L)
            assert(data?.mediaFiles?.totalCount == 23L)
            assert(data?.conversations != null)
            assert(data?.devices != null)
        }

        @Test
        fun `should forward pagination cursors and page size to the use case unchanged`() {
            val export = sampleExport()
            every {
                manageUserDataUseCase.exportUserData(userId, "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", 50)
            } returns export

            controller.exportUserData(
                messagesCursor = "2026-01-01T00:00:00Z",
                mediaCursor = "2026-02-01T00:00:00Z",
                pageSize = 50
            )

            verify { manageUserDataUseCase.exportUserData(userId, "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", 50) }
        }

        @Test
        fun `should throw USER_NOT_FOUND for deleted user`() {
            every {
                manageUserDataUseCase.exportUserData(userId, null, null, 200)
            } throws BusinessException(ErrorCode.USER_NOT_FOUND)

            try {
                controller.exportUserData(messagesCursor = null, mediaCursor = null, pageSize = 200)
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

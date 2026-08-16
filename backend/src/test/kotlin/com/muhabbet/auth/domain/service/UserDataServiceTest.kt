package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.model.DeviceLinkStatus
import com.muhabbet.auth.domain.model.ExportedBroadcastList
import com.muhabbet.auth.domain.model.ExportedChatFolder
import com.muhabbet.auth.domain.model.ExportedChatWallpaper
import com.muhabbet.auth.domain.model.ExportedContact
import com.muhabbet.auth.domain.model.ExportedConversationMembership
import com.muhabbet.auth.domain.model.ExportedEncryptionKeySummary
import com.muhabbet.auth.domain.model.ExportedMediaFile
import com.muhabbet.auth.domain.model.ExportedMessage
import com.muhabbet.auth.domain.model.ExportedMessageBackup
import com.muhabbet.auth.domain.model.LoginApproval
import com.muhabbet.auth.domain.model.LoginApprovalStatus
import com.muhabbet.auth.domain.model.MessageDirection
import com.muhabbet.auth.domain.port.out.DeviceLinkSessionRepository
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.LoginApprovalRepository
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserDataQueryPort
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UserDataServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var userDataQueryPort: UserDataQueryPort
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var loginApprovalRepository: LoginApprovalRepository
    private lateinit var deviceLinkSessionRepository: DeviceLinkSessionRepository
    private lateinit var phoneHashRepository: PhoneHashRepository
    private lateinit var service: UserDataService

    private val userId = TestData.USER_ID_1

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        refreshTokenRepository = mockk()
        userDataQueryPort = mockk()
        deviceRepository = mockk()
        loginApprovalRepository = mockk()
        deviceLinkSessionRepository = mockk()
        phoneHashRepository = mockk()
        service = UserDataService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            userDataQueryPort = userDataQueryPort,
            deviceRepository = deviceRepository,
            loginApprovalRepository = loginApprovalRepository,
            deviceLinkSessionRepository = deviceLinkSessionRepository,
            phoneHashRepository = phoneHashRepository
        )
    }

    // ─── Helpers ──────────────────────────────────────────────

    private fun stubEmptyExportDependencies() {
        every { refreshTokenRepository.findByUserId(userId) } returns emptyList()
        every { deviceRepository.findByUserId(userId) } returns emptyList()
        every { loginApprovalRepository.findByUserId(userId) } returns emptyList()
        every { deviceLinkSessionRepository.findByUserId(userId) } returns emptyList()
        every { phoneHashRepository.existsByUserId(userId) } returns false
        every { userDataQueryPort.findContacts(userId) } returns emptyList()
        every { userDataQueryPort.findConversationMemberships(userId) } returns emptyList()
        every { userDataQueryPort.findChatWallpapers(userId) } returns emptyList()
        every { userDataQueryPort.findChatFolders(userId) } returns emptyList()
        every { userDataQueryPort.findMessageBackups(userId) } returns emptyList()
        every { userDataQueryPort.findOwnedBroadcastLists(userId) } returns emptyList()
        every { userDataQueryPort.findBroadcastListMemberships(userId) } returns emptyList()
        every { userDataQueryPort.findEncryptionKeySummary(userId) } returns
            ExportedEncryptionKeySummary(registered = false, registeredAt = null, keyVersion = null, unusedOneTimePreKeyCount = 0)
        every { userDataQueryPort.findMessagesPage(eq(userId), any(), any()) } returns emptyList()
        every { userDataQueryPort.countMessagesByUserId(userId) } returns 0
        every { userDataQueryPort.findMediaFilesPage(eq(userId), any(), any()) } returns emptyList()
        every { userDataQueryPort.countMediaFilesByUserId(userId) } returns 0
    }

    @Nested
    inner class ExportUserData {

        @Test
        fun `should throw USER_NOT_FOUND when the user does not exist`() {
            every { userRepository.findById(userId) } returns null

            val ex = assertThrows(BusinessException::class.java) { service.exportUserData(userId) }
            assertEquals(ErrorCode.USER_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should export the user's real profile fields, not a summary`() {
            val user = TestData.user(
                id = userId,
                phoneNumber = TestData.PHONE_1,
                displayName = "Ada",
                about = "hello there"
            ).copy(
                readReceiptsEnabled = false,
                onlineStatusVisibility = "contacts",
                aboutVisibility = "nobody",
                twoStepPinHash = "some-bcrypt-hash"
            )
            every { userRepository.findById(userId) } returns user
            stubEmptyExportDependencies()

            val export = service.exportUserData(userId)

            assertEquals(userId, export.profile.userId)
            assertEquals(TestData.PHONE_1, export.profile.phoneNumber)
            assertEquals("Ada", export.profile.displayName)
            assertEquals("hello there", export.profile.about)
            assertTrue(export.profile.twoStepVerificationEnabled, "a non-null PIN hash means 2FA is enabled")
            assertFalse(export.privacySettings.readReceiptsEnabled)
            assertEquals("contacts", export.privacySettings.onlineStatusVisibility)
            assertEquals("nobody", export.privacySettings.aboutVisibility)
        }

        @Test
        fun `should report two-step verification disabled when no PIN hash is set`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId)
            stubEmptyExportDependencies()

            val export = service.exportUserData(userId)

            assertFalse(export.profile.twoStepVerificationEnabled)
        }

        @Test
        fun `should map devices sessions login approvals and linked device sessions from their own repositories`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId)
            stubEmptyExportDependencies()

            val deviceId = TestData.DEVICE_ID_1
            every { deviceRepository.findByUserId(userId) } returns listOf(
                Device(id = deviceId, userId = userId, platform = "android", isPrimary = true)
            )
            every { refreshTokenRepository.findByUserId(userId) } returns listOf(
                RefreshTokenRecord(userId = userId, deviceId = deviceId, tokenHash = "irrelevant-in-export", expiresAt = Instant.now())
            )
            every { loginApprovalRepository.findByUserId(userId) } returns listOf(
                LoginApproval(userId = userId, status = LoginApprovalStatus.APPROVED, deviceName = "New Phone")
            )
            every { deviceLinkSessionRepository.findByUserId(userId) } returns listOf(
                DeviceLinkSession(userId = userId, primaryDeviceId = deviceId, linkToken = "secret-token", expiresAt = Instant.now(), status = DeviceLinkStatus.COMPLETED)
            )

            val export = service.exportUserData(userId)

            assertEquals(1, export.devices.size)
            assertEquals(deviceId, export.devices.single().id)
            assertEquals(1, export.sessions.size)
            assertEquals(deviceId, export.sessions.single().deviceId)
            assertEquals(1, export.loginApprovals.size)
            assertEquals("New Phone", export.loginApprovals.single().deviceName)
            assertEquals(1, export.linkedDeviceSessions.size)
            assertEquals("COMPLETED", export.linkedDeviceSessions.single().status)
        }

        @Test
        fun `should not expose the refresh token hash anywhere in the exported session`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId)
            stubEmptyExportDependencies()
            val deviceId = TestData.DEVICE_ID_1
            every { refreshTokenRepository.findByUserId(userId) } returns listOf(
                RefreshTokenRecord(userId = userId, deviceId = deviceId, tokenHash = "should-never-leave-the-server", expiresAt = Instant.now())
            )

            val export = service.exportUserData(userId)

            // ExportedSession has no tokenHash field at all — this asserts the shape holds a
            // deviceId/timestamps only, i.e. the secret has nowhere to go even by accident.
            val session = export.sessions.single()
            assertEquals(deviceId, session.deviceId)
        }

        @Test
        fun `should default to epoch and page size 200 when no cursor or page size is given`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId)
            stubEmptyExportDependencies()

            service.exportUserData(userId)

            verify { userDataQueryPort.findMessagesPage(userId, Instant.EPOCH, 200) }
            verify { userDataQueryPort.findMediaFilesPage(userId, Instant.EPOCH, 200) }
        }

        @Test
        fun `should parse a valid cursor and use it as the since timestamp`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId)
            stubEmptyExportDependencies()
            val cursor = Instant.parse("2026-01-01T00:00:00Z")

            service.exportUserData(userId, messagesCursor = cursor.toString())

            verify { userDataQueryPort.findMessagesPage(userId, cursor, 200) }
        }

        @Test
        fun `should fall back to epoch when the cursor cannot be parsed`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId)
            stubEmptyExportDependencies()

            service.exportUserData(userId, messagesCursor = "not-a-timestamp")

            verify { userDataQueryPort.findMessagesPage(userId, Instant.EPOCH, 200) }
        }

        @Test
        fun `should clamp an oversized page size down to the maximum`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId)
            stubEmptyExportDependencies()

            service.exportUserData(userId, pageSize = 100_000)

            verify { userDataQueryPort.findMessagesPage(userId, Instant.EPOCH, UserDataService.MAX_EXPORT_PAGE_SIZE) }
        }

        @Test
        fun `should clamp a zero or negative page size up to at least one`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId)
            stubEmptyExportDependencies()

            service.exportUserData(userId, pageSize = 0)

            verify { userDataQueryPort.findMessagesPage(userId, Instant.EPOCH, 1) }
        }

        @Test
        fun `should surface hasMore and total count from the messages page`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId)
            stubEmptyExportDependencies()
            val now = Instant.now()
            val page = (1..3).map { i ->
                ExportedMessage(
                    id = UUID.randomUUID(), conversationId = TestData.CONVERSATION_ID,
                    direction = MessageDirection.SENT, counterpartyDisplayName = null,
                    contentType = "text", content = "msg$i", mediaUrl = null,
                    replyToId = null, forwardedFromId = null,
                    serverTimestamp = now.plusSeconds(i.toLong()), clientTimestamp = now,
                    editedAt = null, isDeleted = false
                )
            }
            every { userDataQueryPort.findMessagesPage(eq(userId), any(), eq(2)) } returns page
            every { userDataQueryPort.countMessagesByUserId(userId) } returns 4_812L

            val export = service.exportUserData(userId, pageSize = 2)

            assertEquals(2, export.messages.items.size, "the limit+1'th row is a probe, not a real item")
            assertTrue(export.messages.hasMore)
            assertEquals(4_812L, export.messages.totalCount)
        }

        @Test
        fun `should pass through every other export category from the query port unchanged`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId)
            stubEmptyExportDependencies()

            val contact = ExportedContact(contactUserId = TestData.USER_ID_2, nickname = "Aslı", isBlocked = false, createdAt = Instant.now())
            val membership = ExportedConversationMembership(
                conversationId = TestData.CONVERSATION_ID, type = "direct", name = null,
                otherParticipantDisplayName = "Aslı", role = "member", joinedAt = Instant.now(),
                mutedUntil = null, pinned = false, archived = false, lastReadAt = null
            )
            val wallpaper = ExportedChatWallpaper(conversationId = null, wallpaperType = "DEFAULT", createdAt = Instant.now())
            val folder = ExportedChatFolder(id = UUID.randomUUID(), name = "Work", position = 0, conversationIds = emptyList(), createdAt = Instant.now())
            val backup = ExportedMessageBackup(
                id = UUID.randomUUID(), status = "COMPLETED", fileSizeBytes = 1024, messageCount = 10,
                conversationCount = 2, startedAt = Instant.now(), completedAt = Instant.now(), expiresAt = null
            )
            val broadcastList = ExportedBroadcastList(id = UUID.randomUUID(), name = "Announcements", memberCount = 5, createdAt = Instant.now())
            val media = ExportedMediaFile(id = UUID.randomUUID(), contentType = "image/jpeg", sizeBytes = 2048, originalFilename = "photo.jpg", durationSeconds = null, createdAt = Instant.now())

            every { userDataQueryPort.findContacts(userId) } returns listOf(contact)
            every { userDataQueryPort.findConversationMemberships(userId) } returns listOf(membership)
            every { userDataQueryPort.findChatWallpapers(userId) } returns listOf(wallpaper)
            every { userDataQueryPort.findChatFolders(userId) } returns listOf(folder)
            every { userDataQueryPort.findMessageBackups(userId) } returns listOf(backup)
            every { userDataQueryPort.findOwnedBroadcastLists(userId) } returns listOf(broadcastList)
            every { userDataQueryPort.findBroadcastListMemberships(userId) } returns listOf(TestData.CONVERSATION_ID)
            every { userDataQueryPort.findMediaFilesPage(eq(userId), any(), any()) } returns listOf(media)
            every { phoneHashRepository.existsByUserId(userId) } returns true

            val export = service.exportUserData(userId)

            assertEquals(listOf(contact), export.contacts)
            assertEquals(listOf(membership), export.conversations)
            assertEquals(listOf(wallpaper), export.chatWallpapers)
            assertEquals(listOf(folder), export.chatFolders)
            assertEquals(listOf(backup), export.messageBackups)
            assertEquals(listOf(broadcastList), export.ownedBroadcastLists)
            assertEquals(listOf(TestData.CONVERSATION_ID), export.broadcastListMemberships)
            assertEquals(listOf(media), export.mediaFiles.items)
            assertTrue(export.discoverableByPhoneHash)
        }
    }

    @Nested
    inner class RequestAccountDeletion {

        @Test
        fun `should throw USER_ALREADY_DELETED for an already-deleted account`() {
            every { userRepository.findById(userId) } returns TestData.user(id = userId).copy(status = com.muhabbet.auth.domain.model.UserStatus.DELETED)

            val ex = assertThrows(BusinessException::class.java) { service.requestAccountDeletion(userId) }
            assertEquals(ErrorCode.USER_ALREADY_DELETED, ex.errorCode)
        }

        @Test
        fun `should erase personal data and anonymise the phone number`() {
            val user = TestData.user(id = userId, phoneNumber = TestData.PHONE_1)
            every { userRepository.findById(userId) } returns user
            every { refreshTokenRepository.revokeAllForUser(userId) } returns Unit
            every { userDataQueryPort.removeUserFromAllConversations(userId) } returns Unit
            every { userDataQueryPort.erasePersonalData(userId) } returns Unit
            every { userRepository.save(any()) } answers { firstArg() }

            service.requestAccountDeletion(userId)

            verify { userDataQueryPort.erasePersonalData(userId) }
            verify {
                userRepository.save(match {
                    it.phoneNumber != TestData.PHONE_1 && it.status == com.muhabbet.auth.domain.model.UserStatus.DELETED
                })
            }
        }
    }
}

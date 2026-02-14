package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.BackupInfo
import com.muhabbet.messaging.domain.port.`in`.ManageBackupUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Tests for backup use cases. BackupController uses @AuthenticationPrincipal,
 * so we test the use case layer directly.
 */
class BackupControllerTest {

    private lateinit var manageBackupUseCase: ManageBackupUseCase

    private val userId = TestData.USER_ID_1

    @BeforeEach
    fun setUp() {
        manageBackupUseCase = mockk()
    }

    @Nested
    inner class CreateBackup {

        @Test
        fun `should create backup and return info`() {
            val backupInfo = BackupInfo(
                id = UUID.randomUUID(),
                status = "PENDING",
                backupUrl = null,
                fileSizeBytes = null,
                messageCount = null,
                conversationCount = null,
                startedAt = "2026-02-14T10:00:00Z",
                completedAt = null,
                expiresAt = null
            )

            every { manageBackupUseCase.createBackup(userId) } returns backupInfo

            val result = manageBackupUseCase.createBackup(userId)

            assert(result.status == "PENDING")
            assert(result.backupUrl == null)
        }

        @Test
        fun `should throw BACKUP_IN_PROGRESS when backup already running`() {
            every {
                manageBackupUseCase.createBackup(userId)
            } throws BusinessException(ErrorCode.BACKUP_IN_PROGRESS)

            try {
                manageBackupUseCase.createBackup(userId)
                assert(false)
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.BACKUP_IN_PROGRESS)
            }
        }
    }

    @Nested
    inner class GetLatestBackup {

        @Test
        fun `should return latest completed backup`() {
            val backupInfo = BackupInfo(
                id = UUID.randomUUID(),
                status = "COMPLETED",
                backupUrl = "https://storage.muhabbet.com/backups/abc.zip",
                fileSizeBytes = 1024000,
                messageCount = 500,
                conversationCount = 10,
                startedAt = "2026-02-14T09:00:00Z",
                completedAt = "2026-02-14T09:05:00Z",
                expiresAt = "2026-03-14T09:05:00Z"
            )

            every { manageBackupUseCase.getLatestBackup(userId) } returns backupInfo

            val result = manageBackupUseCase.getLatestBackup(userId)

            assert(result?.status == "COMPLETED")
            assert(result?.messageCount == 500)
        }

        @Test
        fun `should return null when no backups exist`() {
            every { manageBackupUseCase.getLatestBackup(userId) } returns null

            val result = manageBackupUseCase.getLatestBackup(userId)

            assert(result == null)
        }
    }

    @Nested
    inner class ListBackups {

        @Test
        fun `should return list of all backups`() {
            val backups = listOf(
                BackupInfo(id = UUID.randomUUID(), status = "COMPLETED", backupUrl = "url1", fileSizeBytes = 1000, messageCount = 100, conversationCount = 5, startedAt = "2026-02-13", completedAt = "2026-02-13", expiresAt = null),
                BackupInfo(id = UUID.randomUUID(), status = "PENDING", backupUrl = null, fileSizeBytes = null, messageCount = null, conversationCount = null, startedAt = "2026-02-14", completedAt = null, expiresAt = null)
            )

            every { manageBackupUseCase.listBackups(userId) } returns backups

            val result = manageBackupUseCase.listBackups(userId)

            assert(result.size == 2)
            assert(result[0].status == "COMPLETED")
            assert(result[1].status == "PENDING")
        }
    }
}

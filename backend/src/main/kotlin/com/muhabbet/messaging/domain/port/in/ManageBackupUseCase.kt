package com.muhabbet.messaging.domain.port.`in`

import java.util.UUID

data class BackupInfo(
    val id: UUID,
    val status: String,
    val backupUrl: String?,
    val fileSizeBytes: Long?,
    val messageCount: Int?,
    val conversationCount: Int?,
    val startedAt: String,
    val completedAt: String?,
    val expiresAt: String?
)

interface ManageBackupUseCase {
    /** Initiate a new message backup for the user. Returns backup ID. */
    fun createBackup(userId: UUID): BackupInfo

    /** Get the latest backup status for the user. */
    fun getLatestBackup(userId: UUID): BackupInfo?

    /** List all backups for the user. */
    fun listBackups(userId: UUID): List<BackupInfo>
}

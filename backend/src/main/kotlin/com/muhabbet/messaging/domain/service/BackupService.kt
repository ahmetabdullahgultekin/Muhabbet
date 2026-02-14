package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.`in`.BackupInfo
import com.muhabbet.messaging.domain.port.`in`.ManageBackupUseCase
import com.muhabbet.messaging.domain.port.out.BackupRepository
import com.muhabbet.messaging.domain.port.out.MessageBackup
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class BackupService(
    private val backupRepository: BackupRepository
) : ManageBackupUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun createBackup(userId: UUID): BackupInfo {
        val backup = backupRepository.save(
            MessageBackup(userId = userId, status = "PENDING")
        )
        log.info("Backup created: id={}, userId={}", backup.id, userId)
        // In production, this would trigger an async job to:
        // 1. Query all user's conversations and messages
        // 2. Serialize to JSON/encrypted archive
        // 3. Upload to MinIO
        // 4. Update backup record with URL and size
        // For now, mark as completed with placeholder
        val completedAt = Instant.now()
        val expiresAt = completedAt.plusSeconds(7 * 24 * 3600) // 7 days
        backupRepository.updateStatus(
            id = backup.id,
            status = "COMPLETED",
            backupUrl = null, // Would be a MinIO pre-signed URL
            fileSizeBytes = 0,
            messageCount = 0,
            conversationCount = 0,
            completedAt = completedAt,
            expiresAt = expiresAt
        )
        return backup.copy(status = "COMPLETED", completedAt = completedAt, expiresAt = expiresAt).toInfo()
    }

    @Transactional(readOnly = true)
    override fun getLatestBackup(userId: UUID): BackupInfo? {
        return backupRepository.findLatestByUserId(userId)?.toInfo()
    }

    @Transactional(readOnly = true)
    override fun listBackups(userId: UUID): List<BackupInfo> {
        return backupRepository.findByUserId(userId).map { it.toInfo() }
    }

    private fun MessageBackup.toInfo() = BackupInfo(
        id = id,
        status = status,
        backupUrl = backupUrl,
        fileSizeBytes = fileSizeBytes,
        messageCount = messageCount,
        conversationCount = conversationCount,
        startedAt = startedAt.toString(),
        completedAt = completedAt?.toString(),
        expiresAt = expiresAt?.toString()
    )
}

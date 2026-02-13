package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.domain.port.out.BackupRepository
import com.muhabbet.messaging.domain.port.out.MessageBackup
import com.muhabbet.messaging.adapter.out.persistence.entity.MessageBackupJpaEntity
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class BackupPersistenceAdapter(
    private val springDataBackupRepository: SpringDataBackupRepository
) : BackupRepository {

    override fun save(backup: MessageBackup): MessageBackup {
        val entity = MessageBackupJpaEntity(
            id = backup.id,
            userId = backup.userId,
            status = backup.status,
            startedAt = backup.startedAt
        )
        return springDataBackupRepository.save(entity).toDomain()
    }

    override fun findById(id: UUID): MessageBackup? {
        return springDataBackupRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findLatestByUserId(userId: UUID): MessageBackup? {
        return springDataBackupRepository.findFirstByUserIdOrderByStartedAtDesc(userId)?.toDomain()
    }

    override fun findByUserId(userId: UUID): List<MessageBackup> {
        return springDataBackupRepository.findByUserIdOrderByStartedAtDesc(userId).map { it.toDomain() }
    }

    override fun updateStatus(
        id: UUID, status: String, backupUrl: String?, fileSizeBytes: Long?,
        messageCount: Int?, conversationCount: Int?, completedAt: Instant?, expiresAt: Instant?
    ) {
        springDataBackupRepository.findById(id).ifPresent { entity ->
            entity.status = status
            entity.backupUrl = backupUrl
            entity.fileSizeBytes = fileSizeBytes
            entity.messageCount = messageCount
            entity.conversationCount = conversationCount
            entity.completedAt = completedAt
            entity.expiresAt = expiresAt
            springDataBackupRepository.save(entity)
        }
    }

    private fun MessageBackupJpaEntity.toDomain() = MessageBackup(
        id = id,
        userId = userId,
        status = status,
        backupUrl = backupUrl,
        fileSizeBytes = fileSizeBytes,
        messageCount = messageCount,
        conversationCount = conversationCount,
        startedAt = startedAt,
        completedAt = completedAt,
        expiresAt = expiresAt,
        errorMessage = errorMessage
    )
}

package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageBackupJpaEntity
import com.muhabbet.messaging.domain.model.BackupStatus
import com.muhabbet.messaging.domain.model.MessageBackup
import com.muhabbet.messaging.domain.port.out.BackupRepository
import org.springframework.stereotype.Component
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

    override fun finish(finished: MessageBackup): Boolean {
        // EXPIRED is derived, never stored; PENDING is not an ending.
        if (finished.status != BackupStatus.COMPLETED && finished.status != BackupStatus.FAILED) return false
        return springDataBackupRepository.finish(
            id = finished.id,
            status = finished.status.name,
            backupUrl = finished.backupUrl,
            fileSizeBytes = finished.fileSizeBytes,
            messageCount = finished.messageCount,
            conversationCount = finished.conversationCount,
            completedAt = finished.completedAt,
            expiresAt = finished.expiresAt,
            errorMessage = finished.errorMessage
        ) == 1
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

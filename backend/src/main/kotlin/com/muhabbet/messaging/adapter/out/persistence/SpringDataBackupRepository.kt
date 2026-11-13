package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageBackupJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataBackupRepository : JpaRepository<MessageBackupJpaEntity, UUID> {
    fun findFirstByUserIdOrderByStartedAtDesc(userId: UUID): MessageBackupJpaEntity?
    fun findByUserIdOrderByStartedAtDesc(userId: UUID): List<MessageBackupJpaEntity>

    /** Conditional on PENDING; 0 rows means the backup had already ended. */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE message_backups
            SET status = :status, backup_url = :backupUrl, file_size_bytes = :fileSizeBytes,
                message_count = :messageCount, conversation_count = :conversationCount,
                completed_at = :completedAt, expires_at = :expiresAt, error_message = :errorMessage
            WHERE id = :id AND status = 'PENDING'
        """,
        nativeQuery = true
    )
    fun finish(
        id: UUID,
        status: String,
        backupUrl: String?,
        fileSizeBytes: Long?,
        messageCount: Int?,
        conversationCount: Int?,
        completedAt: Instant?,
        expiresAt: Instant?,
        errorMessage: String?
    ): Int
}

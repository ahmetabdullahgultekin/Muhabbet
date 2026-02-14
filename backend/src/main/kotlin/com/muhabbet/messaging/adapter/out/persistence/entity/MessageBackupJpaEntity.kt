package com.muhabbet.messaging.adapter.out.persistence.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "message_backups")
class MessageBackupJpaEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(nullable = false, length = 20) var status: String = "PENDING",
    @Column(name = "backup_url") var backupUrl: String? = null,
    @Column(name = "file_size_bytes") var fileSizeBytes: Long? = null,
    @Column(name = "message_count") var messageCount: Int? = null,
    @Column(name = "conversation_count") var conversationCount: Int? = null,
    @Column(name = "started_at", nullable = false) val startedAt: Instant = Instant.now(),
    @Column(name = "completed_at") var completedAt: Instant? = null,
    @Column(name = "expires_at") var expiresAt: Instant? = null,
    @Column(name = "error_message") var errorMessage: String? = null
)

package com.muhabbet.messaging.domain.port.out

import java.time.Instant
import java.util.UUID

data class MessageBackup(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val status: String = "PENDING",
    val backupUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val messageCount: Int? = null,
    val conversationCount: Int? = null,
    val startedAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val errorMessage: String? = null
)

interface BackupRepository {
    fun save(backup: MessageBackup): MessageBackup
    fun findById(id: UUID): MessageBackup?
    fun findLatestByUserId(userId: UUID): MessageBackup?
    fun findByUserId(userId: UUID): List<MessageBackup>
    fun updateStatus(id: UUID, status: String, backupUrl: String?, fileSizeBytes: Long?,
                     messageCount: Int?, conversationCount: Int?, completedAt: Instant?, expiresAt: Instant?)
}

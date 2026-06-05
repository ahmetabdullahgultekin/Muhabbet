package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.BackupInfo
import com.muhabbet.messaging.domain.port.`in`.ManageBackupUseCase
import com.muhabbet.messaging.domain.port.out.BackupArchivePort
import com.muhabbet.messaging.domain.port.out.BackupRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBackup
import com.muhabbet.messaging.domain.port.out.MessageRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Produces a real message-backup archive: serializes the requesting user's conversations and
 * messages to JSON, uploads the archive to object storage via [BackupArchivePort], and records
 * the resulting presigned URL + counts on the backup row.
 *
 * Replaces the previous placeholder that immediately marked backups COMPLETED with a null URL
 * and zero counts (no archive was ever produced — restore was impossible).
 *
 * Failure handling: any error during build/upload marks the backup FAILED (rather than a false
 * COMPLETED) so the UX and any retry logic can react truthfully.
 */
open class BackupService(
    private val backupRepository: BackupRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val backupArchivePort: BackupArchivePort
) : ManageBackupUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    @Transactional
    override fun createBackup(userId: UUID): BackupInfo {
        val backup = backupRepository.save(
            MessageBackup(userId = userId, status = "PENDING")
        )
        log.info("Backup started: id={}, userId={}", backup.id, userId)

        return try {
            val conversations = conversationRepository.findConversationsByUserId(userId)
            var messageCount = 0
            val convArchives = conversations.map { conv ->
                val messages = fetchAllMessages(conv.id)
                messageCount += messages.size
                ConversationArchive(
                    conversationId = conv.id.toString(),
                    messages = messages.map { it.toArchive() }
                )
            }

            val archive = BackupArchive(
                version = ARCHIVE_VERSION,
                userId = userId.toString(),
                createdAt = Instant.now().toString(),
                conversationCount = convArchives.size,
                messageCount = messageCount,
                conversations = convArchives
            )
            val bytes = json.encodeToString(BackupArchive.serializer(), archive).encodeToByteArray()

            val stored = backupArchivePort.upload(
                userId = userId,
                backupId = backup.id,
                bytes = bytes
            )

            val completedAt = Instant.now()
            val expiresAt = completedAt.plusSeconds(EXPIRY_SECONDS)
            backupRepository.updateStatus(
                id = backup.id,
                status = "COMPLETED",
                backupUrl = stored.presignedUrl,
                fileSizeBytes = bytes.size.toLong(),
                messageCount = messageCount,
                conversationCount = convArchives.size,
                completedAt = completedAt,
                expiresAt = expiresAt
            )
            log.info(
                "Backup completed: id={}, conversations={}, messages={}, bytes={}",
                backup.id, convArchives.size, messageCount, bytes.size
            )
            backup.copy(
                status = "COMPLETED",
                backupUrl = stored.presignedUrl,
                fileSizeBytes = bytes.size.toLong(),
                messageCount = messageCount,
                conversationCount = convArchives.size,
                completedAt = completedAt,
                expiresAt = expiresAt
            ).toInfo()
        } catch (e: Exception) {
            log.error("Backup failed: id={}, userId={}: {}", backup.id, userId, e.message, e)
            backupRepository.updateStatus(
                id = backup.id,
                status = "FAILED",
                backupUrl = null,
                fileSizeBytes = null,
                messageCount = null,
                conversationCount = null,
                completedAt = Instant.now(),
                expiresAt = null
            )
            backup.copy(status = "FAILED").toInfo()
        }
    }

    /** Page through a conversation's messages so a large history doesn't load in one shot. */
    private fun fetchAllMessages(conversationId: UUID): List<Message> {
        val all = mutableListOf<Message>()
        var before: Instant? = null
        while (true) {
            val page = messageRepository.findByConversationId(conversationId, before, PAGE_SIZE)
            if (page.isEmpty()) break
            all += page
            if (page.size < PAGE_SIZE || all.size >= MAX_MESSAGES_PER_CONVERSATION) break
            // findByConversationId returns newest-first within the window; page the next window
            // using the oldest serverTimestamp seen so far.
            before = page.minOf { it.serverTimestamp }
        }
        return all
    }

    @Transactional(readOnly = true)
    override fun getLatestBackup(userId: UUID): BackupInfo? {
        return backupRepository.findLatestByUserId(userId)?.toInfo()
    }

    @Transactional(readOnly = true)
    override fun listBackups(userId: UUID): List<BackupInfo> {
        return backupRepository.findByUserId(userId).map { it.toInfo() }
    }

    private fun Message.toArchive() = MessageArchive(
        id = id.toString(),
        senderId = senderId.toString(),
        contentType = contentType.name,
        content = content,
        replyToId = replyToId?.toString(),
        mediaUrl = mediaUrl,
        thumbnailUrl = thumbnailUrl,
        serverTimestamp = serverTimestamp.toString(),
        editedAt = editedAt?.toString(),
        isDeleted = isDeleted,
        forwardedFrom = forwardedFrom?.toString()
    )

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

    companion object {
        const val ARCHIVE_VERSION = 1
        const val PAGE_SIZE = 200
        const val MAX_MESSAGES_PER_CONVERSATION = 50_000
        const val EXPIRY_SECONDS = 7L * 24 * 3600 // 7 days
    }
}

// ─── Archive DTOs (the serialized backup format) ──────────────────────────

@Serializable
data class BackupArchive(
    val version: Int,
    val userId: String,
    val createdAt: String,
    val conversationCount: Int,
    val messageCount: Int,
    val conversations: List<ConversationArchive>
)

@Serializable
data class ConversationArchive(
    val conversationId: String,
    val messages: List<MessageArchive>
)

@Serializable
data class MessageArchive(
    val id: String,
    val senderId: String,
    val contentType: String,
    val content: String,
    val replyToId: String? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val serverTimestamp: String,
    val editedAt: String? = null,
    val isDeleted: Boolean = false,
    val forwardedFrom: String? = null
)

package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.BackupTransition
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageBackup
import com.muhabbet.messaging.domain.port.`in`.BackupInfo
import com.muhabbet.messaging.domain.port.`in`.ManageBackupUseCase
import com.muhabbet.messaging.domain.port.out.BackupArchivePort
import com.muhabbet.messaging.domain.port.out.BackupRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Produces a real message-backup archive: serializes the requesting user's conversations and
 * messages to JSON, uploads the archive to object storage via [BackupArchivePort], and records
 * the resulting presigned URL + counts on the backup row.
 *
 * The lifecycle is [MessageBackup]'s: PENDING -> COMPLETED | FAILED, with EXPIRED derived (#897).
 * Three transactions, not one. The PENDING row commits on its own, the build and upload run outside
 * any transaction, and the ending is written in a transaction of its own. Before, all of it ran in
 * one `@Transactional` method, so a failure thrown by a transactional repository during the build
 * marked the transaction rollback-only and took the FAILED update — and the PENDING row — down with
 * it: the user's backup simply vanished. It also held a pool connection across an upload.
 */
open class BackupService(
    private val backupRepository: BackupRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val backupArchivePort: BackupArchivePort,
    private val transactions: TransactionRunner,
    private val clock: Clock = Clock.systemUTC()
) : ManageBackupUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    override fun createBackup(userId: UUID): BackupInfo {
        val requested = transactions.inTransaction {
            backupRepository.save(MessageBackup.request(userId, clock.instant()))
        }
        log.info("Backup started: id={}, userId={}", requested.id, userId)

        val transition = try {
            build(requested)
        } catch (e: Exception) {
            log.error("Backup failed: id={}, userId={}: {}", requested.id, userId, e.message, e)
            // The kind of failure, not its message: a message can carry content or paths.
            requested.fail(reason = e.javaClass.simpleName, now = clock.instant())
        }

        val finished = when (transition) {
            is BackupTransition.Moved -> transition.backup
            // Unreachable while `requested` is the row this call just created, which is PENDING.
            is BackupTransition.Rejected -> error("backup ${requested.id} left PENDING before it was built")
        }
        transactions.inTransaction { backupRepository.finish(finished) }
        return finished.toInfo()
    }

    private fun build(requested: MessageBackup): BackupTransition {
        val conversations = conversationRepository.findConversationsByUserId(requested.userId)
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
            userId = requested.userId.toString(),
            createdAt = clock.instant().toString(),
            conversationCount = convArchives.size,
            messageCount = messageCount,
            conversations = convArchives
        )
        val bytes = json.encodeToString(BackupArchive.serializer(), archive).encodeToByteArray()

        val stored = backupArchivePort.upload(
            userId = requested.userId,
            backupId = requested.id,
            bytes = bytes
        )
        log.info(
            "Backup completed: id={}, conversations={}, messages={}, bytes={}",
            requested.id, convArchives.size, messageCount, bytes.size
        )
        return requested.complete(
            backupUrl = stored.presignedUrl,
            fileSizeBytes = bytes.size.toLong(),
            messageCount = messageCount,
            conversationCount = convArchives.size,
            now = clock.instant(),
            validFor = EXPIRY_SECONDS
        )
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
        // A backup restores what the app is still willing to show, and it will never show this
        // again. `media_url` is a presigned URL that needs no credential, so archiving it would make
        // the backup a way of keeping a view-once photo indefinitely (#515).
        mediaUrl = if (viewOnce) null else mediaUrl,
        thumbnailUrl = if (viewOnce) null else thumbnailUrl,
        serverTimestamp = serverTimestamp.toString(),
        editedAt = editedAt?.toString(),
        isDeleted = isDeleted,
        forwardedFrom = forwardedFrom?.toString()
    )

    private fun MessageBackup.toInfo() = BackupInfo(
        id = id,
        status = effectiveStatus(clock.instant()).name,
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

package com.muhabbet.app.data.local

import com.muhabbet.app.db.MuhabbetDatabase
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.MemberRole
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalCache(driverFactory: DatabaseDriverFactory) {

    private val database = MuhabbetDatabase(driverFactory.createDriver())
    private val queries = database.muhabbetDatabaseQueries
    private val json = Json { ignoreUnknownKeys = true }

    // --- Conversations ---

    fun getConversations(): List<ConversationResponse> {
        return queries.getConversations().executeAsList().map { row ->
            ConversationResponse(
                id = row.id,
                type = ConversationType.valueOf(row.type),
                name = row.name,
                avatarUrl = row.avatarUrl,
                participants = try {
                    json.decodeFromString<List<ParticipantResponse>>(row.participantsJson)
                } catch (_: Exception) { emptyList() },
                lastMessagePreview = row.lastMessagePreview,
                lastMessageAt = row.lastMessageAt,
                unreadCount = row.unreadCount.toInt(),
                createdAt = row.createdAt,
                disappearAfterSeconds = row.disappearAfterSeconds?.toInt(),
                isPinned = row.isPinned != 0L
            )
        }
    }

    fun upsertConversation(conv: ConversationResponse) {
        queries.upsertConversation(
            id = conv.id,
            type = conv.type.name,
            name = conv.name,
            avatarUrl = conv.avatarUrl,
            lastMessagePreview = conv.lastMessagePreview,
            lastMessageAt = conv.lastMessageAt,
            unreadCount = conv.unreadCount.toLong(),
            createdAt = conv.createdAt,
            disappearAfterSeconds = conv.disappearAfterSeconds?.toLong(),
            isPinned = if (conv.isPinned) 1L else 0L,
            participantsJson = json.encodeToString(conv.participants),
            updatedAt = conv.lastMessageAt ?: conv.createdAt
        )
    }

    fun upsertConversations(conversations: List<ConversationResponse>) {
        database.transaction {
            conversations.forEach { upsertConversation(it) }
        }
    }

    fun deleteConversation(id: String) {
        queries.deleteConversation(id)
        queries.deleteMessagesForConversation(id)
    }

    fun clearConversations() {
        queries.clearConversations()
    }

    // --- Messages ---

    fun getMessages(conversationId: String): List<Message> {
        return queries.getMessages(conversationId).executeAsList().map { row ->
            Message(
                id = row.id,
                conversationId = row.conversationId,
                senderId = row.senderId,
                contentType = ContentType.valueOf(row.contentType),
                content = row.content,
                replyToId = row.replyToId,
                mediaUrl = row.mediaUrl,
                thumbnailUrl = row.thumbnailUrl,
                status = MessageStatus.valueOf(row.status),
                serverTimestamp = row.serverTimestamp?.let { safeParseInstant(it) },
                clientTimestamp = safeParseInstant(row.clientTimestamp) ?: Clock.System.now(),
                editedAt = row.editedAt?.let { safeParseInstant(it) },
                isDeleted = row.isDeleted != 0L,
                forwardedFrom = row.forwardedFrom,
                reactions = try {
                    json.decodeFromString<Map<String, Int>>(row.reactionsJson)
                } catch (_: Exception) { emptyMap() },
                myReactions = try {
                    json.decodeFromString<Set<String>>(row.myReactionsJson)
                } catch (_: Exception) { emptySet() }
            )
        }
    }

    fun getMessagesByPage(conversationId: String, limit: Int): List<Message> {
        return queries.getMessagesByPage(conversationId, limit.toLong()).executeAsList().map { row ->
            Message(
                id = row.id,
                conversationId = row.conversationId,
                senderId = row.senderId,
                contentType = ContentType.valueOf(row.contentType),
                content = row.content,
                replyToId = row.replyToId,
                mediaUrl = row.mediaUrl,
                thumbnailUrl = row.thumbnailUrl,
                status = MessageStatus.valueOf(row.status),
                serverTimestamp = row.serverTimestamp?.let { safeParseInstant(it) },
                clientTimestamp = safeParseInstant(row.clientTimestamp) ?: Clock.System.now(),
                editedAt = row.editedAt?.let { safeParseInstant(it) },
                isDeleted = row.isDeleted != 0L,
                forwardedFrom = row.forwardedFrom,
                reactions = try {
                    json.decodeFromString<Map<String, Int>>(row.reactionsJson)
                } catch (_: Exception) { emptyMap() },
                myReactions = try {
                    json.decodeFromString<Set<String>>(row.myReactionsJson)
                } catch (_: Exception) { emptySet() }
            )
        }
    }

    fun upsertMessage(msg: Message) {
        queries.upsertMessage(
            id = msg.id,
            conversationId = msg.conversationId,
            senderId = msg.senderId,
            contentType = msg.contentType.name,
            content = msg.content,
            replyToId = msg.replyToId,
            mediaUrl = msg.mediaUrl,
            thumbnailUrl = msg.thumbnailUrl,
            status = msg.status.name,
            serverTimestamp = msg.serverTimestamp?.toString(),
            clientTimestamp = msg.clientTimestamp.toString(),
            editedAt = msg.editedAt?.toString(),
            isDeleted = if (msg.isDeleted) 1L else 0L,
            forwardedFrom = msg.forwardedFrom,
            reactionsJson = json.encodeToString(msg.reactions),
            myReactionsJson = json.encodeToString(msg.myReactions),
            senderName = null,
            senderAvatarUrl = null
        )
    }

    fun upsertMessages(messages: List<Message>) {
        database.transaction {
            messages.forEach { upsertMessage(it) }
        }
    }

    fun updateMessageStatus(messageId: String, status: MessageStatus) {
        queries.updateMessageStatus(status.name, messageId)
    }

    fun deleteMessage(id: String) {
        queries.deleteMessage(id)
    }

    fun clearMessages() {
        queries.clearMessages()
    }

    // --- Pending Messages (offline queue) ---

    fun getPendingMessages(): List<PendingMessageData> {
        return queries.getPendingMessages().executeAsList().map { row ->
            PendingMessageData(
                id = row.id,
                conversationId = row.conversationId,
                contentType = row.contentType,
                content = row.content,
                replyToId = row.replyToId,
                mediaUrl = row.mediaUrl,
                clientTimestamp = row.clientTimestamp,
                retryCount = row.retryCount.toInt()
            )
        }
    }

    fun insertPendingMessage(msg: PendingMessageData) {
        queries.insertPendingMessage(
            id = msg.id,
            conversationId = msg.conversationId,
            contentType = msg.contentType,
            content = msg.content,
            replyToId = msg.replyToId,
            mediaUrl = msg.mediaUrl,
            clientTimestamp = msg.clientTimestamp,
            retryCount = msg.retryCount.toLong(),
            createdAt = Clock.System.now().toString()
        )
    }

    fun deletePendingMessage(id: String) {
        queries.deletePendingMessage(id)
    }

    fun clearPendingMessages() {
        queries.clearPendingMessages()
    }

    fun incrementRetryCount(id: String) {
        queries.incrementRetryCount(id)
    }

    // --- Helpers ---

    private fun safeParseInstant(value: String): Instant? {
        return try { Instant.parse(value) } catch (_: Exception) { null }
    }
}

data class PendingMessageData(
    val id: String,
    val conversationId: String,
    val contentType: String,
    val content: String,
    val replyToId: String?,
    val mediaUrl: String?,
    val clientTimestamp: String,
    val retryCount: Int = 0
)

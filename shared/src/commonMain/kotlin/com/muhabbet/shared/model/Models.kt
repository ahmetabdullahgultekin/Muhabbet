package com.muhabbet.shared.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

// ─── Enums ───────────────────────────────────────────────

@Serializable
enum class MessageStatus {
    SENDING,    // client-only: queued locally
    SENT,       // server ACKed receipt
    DELIVERED,  // recipient device received
    READ        // recipient opened conversation
}

@Serializable
enum class ContentType {
    TEXT,
    IMAGE,
    VOICE,
    VIDEO,
    DOCUMENT,
    LOCATION,
    CONTACT,
    POLL,
    STICKER,
    GIF
}

@Serializable
enum class ConversationType {
    DIRECT,
    GROUP,
    CHANNEL
}

@Serializable
enum class MemberRole {
    OWNER,
    ADMIN,
    MEMBER
}

@Serializable
enum class PresenceStatus {
    ONLINE,
    OFFLINE,
    TYPING
}

@Serializable
enum class CallType {
    VOICE,
    VIDEO
}

@Serializable
enum class CallEndReason {
    ENDED,
    DECLINED,
    MISSED,
    BUSY,
    FAILED
}

// ─── Domain Models ───────────────────────────────────────

@Serializable
data class Message(
    val id: String,                     // UUIDv7 (client-generated for idempotency)
    val conversationId: String,
    val senderId: String,
    val contentType: ContentType,
    val content: String,                // plaintext for MVP, encrypted blob for Phase 2
    val replyToId: String? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val status: MessageStatus = MessageStatus.SENDING,
    val serverTimestamp: Instant? = null,
    val clientTimestamp: Instant,
    val editedAt: Instant? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class Conversation(
    val id: String,
    val type: ConversationType,
    val name: String? = null,           // null for direct messages
    val avatarUrl: String? = null,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val updatedAt: Instant
)

@Serializable
data class UserProfile(
    val id: String,
    val phoneNumber: String,
    val displayName: String?,
    val avatarUrl: String?,
    val about: String? = null,
    val isOnline: Boolean = false,
    val lastSeenAt: Instant? = null
)

@Serializable
data class Contact(
    val userId: String,
    val displayName: String?,
    val phoneNumber: String,
    val avatarUrl: String?,
    val isRegistered: Boolean          // matched in our system
)

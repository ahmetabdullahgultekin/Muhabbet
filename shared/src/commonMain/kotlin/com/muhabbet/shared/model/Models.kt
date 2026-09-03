package com.muhabbet.shared.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

// ─── Enums ───────────────────────────────────────────────

@Serializable
enum class MessageStatus {
    SENDING,    // client-only: queued locally
    SENT,       // server ACKed receipt
    DELIVERED,  // recipient device received
    READ,       // recipient opened conversation

    /**
     * Client-only, like [SENDING]: the server answered this send with an error ack and will not be
     * sending another one.
     *
     * It exists because there was no way to draw that (#725). A refused message kept the clock
     * [SENDING] gave it, forever — the same picture as a message still on its way, on a message
     * that is never going anywhere. That is worst for a rate-limited send, where the clock invites
     * exactly the retry the limiter is trying to stop, but it was true of every refusal.
     *
     * Never put on the wire in either direction. The server has its own `DeliveryStatus` and no
     * concept of this; a client sending it in a `message.ack` would be ignored.
     */
    FAILED
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

@Serializable
enum class JoinRequestStatus { PENDING, APPROVED, REJECTED }

@Serializable
enum class RsvpStatus { GOING, NOT_GOING, MAYBE }

@Serializable
enum class WallpaperType { DEFAULT, SOLID, CUSTOM }

@Serializable
enum class VisibilityLevel { EVERYONE, CONTACTS, NOBODY }

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
    val isDeleted: Boolean = false,
    val forwardedFrom: String? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val myReactions: Set<String> = emptySet(),
    val viewOnce: Boolean = false,
    /**
     * When a disappearing message is due to vanish, or null if it never does.
     *
     * The server has always known this — `messages.expires_at` is set at send time from the
     * conversation's timer — and never told anyone (#513). A client that cannot see the deadline
     * cannot honour it, so an expired message sat on screen until the user left the chat and came
     * back, which is precisely when the feature is being watched for the first time.
     *
     * Absolute rather than a remaining duration so it survives being read twice: the same payload
     * is used by the live socket frame, the REST history fetch and the background sync, and a
     * relative "expires in 30s" is only true at the instant it was computed. The cost is that the
     * two clocks must roughly agree, which is why the client applies a grace window and errs
     * towards keeping a message a moment too long rather than removing one early — see
     * `MessageExpiry` on the mobile side.
     */
    val expiresAt: Instant? = null,
    /**
     * Whether this view-once message has already been burned.
     *
     * Server-resolved, so "already opened" survives a scroll, a process death and a reinstall. The
     * bubble used to keep this in a `remember`, which meant the refusal lasted exactly as long as
     * the composition did — every way back into the chat offered the seal again.
     *
     * Meaningless unless [viewOnce] is true.
     */
    val viewOnceViewed: Boolean = false
)

@Serializable
data class Conversation(
    val id: String,
    val type: ConversationType,
    val name: String? = null,           // null for direct messages
    val avatarUrl: String? = null,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val updatedAt: Instant,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val isLocked: Boolean = false,
    val announcementOnly: Boolean = false
)

@Serializable
data class UserProfile(
    val id: String,
    // Nullable: only the caller's own profile (GET /users/me) exposes the phone number.
    // Public lookups (GET /users/{id}) return null to prevent phone-number harvesting (KVKK).
    val phoneNumber: String? = null,
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

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
    val viewOnceViewed: Boolean = false,
    /**
     * Whether the user reading this payload has starred this message (#775).
     *
     * Per viewer, resolved by the server in one batch per page — never shared between members. It
     * used to exist only as a `remember`ed set in the chat screen that nothing ever seeded, so every
     * chat opened with every star empty and "Star" re-sent for messages already starred.
     */
    val isStarred: Boolean = false,
    /**
     * The tally of a [ContentType.POLL] message as this viewer sees it, or null for any other
     * message, or when the payload did not carry one (#781).
     *
     * Rides in the history page so a chat with five polls costs no extra request per poll; it used
     * to be fetched by each bubble every time it scrolled back into view. Live changes arrive as
     * `WsMessage.PollUpdated` and replace the counts wholesale.
     */
    val poll: PollTally? = null
)

/**
 * Vote counts of one poll, from one viewer's perspective (#781).
 *
 * [counts] is indexed exactly as the poll's options are (`PollData.options`), so it carries no
 * option text: the text is already in the message body and two copies of it could disagree.
 * [myVote] is the viewer's own option index, or null when they have not voted.
 */
@Serializable
data class PollTally(
    val counts: List<Int>,
    val totalVotes: Int,
    val myVote: Int? = null
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

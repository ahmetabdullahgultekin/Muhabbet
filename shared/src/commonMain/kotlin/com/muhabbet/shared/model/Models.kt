package com.muhabbet.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

// ─── Enums ───────────────────────────────────────────────
//
// Every enum here crosses the wire, and every one is **tolerant**: it carries an `UNKNOWN` constant
// and a [LenientEnumSerializer] that decodes a name this build has never seen to `UNKNOWN` instead
// of failing the frame or page around it (#875). `WireEnumPolicyTest` fails for any `@Serializable`
// enum in this module that does not follow the rule, so a new one cannot quietly opt out.
//
// Two rules follow from it, and they are the whole evolution policy:
//  - **Adding a constant is safe**; an older app reads it as `UNKNOWN` and degrades.
//  - **Renaming or removing one is a breaking change**: the wire name is the contract.
// `UNKNOWN` is a reader's stand-in. Nothing ever sends it on purpose, and a server receiving it
// treats it as a value it cannot act on.

@Serializable(with = MessageStatusSerializer::class)
enum class MessageStatus {
    SENDING,    // client-only: queued locally
    SENT,       // server ACKed receipt
    DELIVERED,  // recipient device received
    READ,       // recipient opened conversation
    UNKNOWN
}

object MessageStatusSerializer :
    LenientEnumSerializer<MessageStatus>("MessageStatus", MessageStatus.entries, MessageStatus.UNKNOWN)

@Serializable(with = ContentTypeSerializer::class)
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
    GIF,

    /** A type added after this build. The app shows "update the app to see this message". */
    UNKNOWN
}

object ContentTypeSerializer :
    LenientEnumSerializer<ContentType>("ContentType", ContentType.entries, ContentType.UNKNOWN)

@Serializable(with = ConversationTypeSerializer::class)
enum class ConversationType {
    DIRECT,
    GROUP,
    CHANNEL,
    UNKNOWN
}

object ConversationTypeSerializer :
    LenientEnumSerializer<ConversationType>("ConversationType", ConversationType.entries, ConversationType.UNKNOWN)

/**
 * A member's role. `UNKNOWN` is a role this build cannot describe, and it grants **nothing**: every
 * permission check compares against a named role, so an unrecognised one fails closed.
 */
@Serializable(with = MemberRoleSerializer::class)
enum class MemberRole {
    OWNER,
    ADMIN,
    MEMBER,
    UNKNOWN
}

object MemberRoleSerializer :
    LenientEnumSerializer<MemberRole>("MemberRole", MemberRole.entries, MemberRole.UNKNOWN)

/**
 * Account presence.
 *
 * [TYPING] is **deprecated**: typing is a per-conversation activity, not a presence. It is what the
 * server put in `presence.update` before `chat.activity` existed (#885), and only a legacy
 * (protocol 0) client is still sent it. Remove it once the minimum supported protocol is 1.
 */
@Serializable(with = PresenceStatusSerializer::class)
enum class PresenceStatus {
    ONLINE,
    OFFLINE,
    TYPING,
    UNKNOWN
}

object PresenceStatusSerializer :
    LenientEnumSerializer<PresenceStatus>("PresenceStatus", PresenceStatus.entries, PresenceStatus.UNKNOWN)

/**
 * What one member is doing in one conversation right now (#885).
 *
 * Separate from [PresenceStatus] because it is a different fact: presence belongs to an account,
 * activity to a (conversation, member) pair, and a group can have several members typing at once.
 */
@Serializable(with = ChatActivityKindSerializer::class)
enum class ChatActivityKind {
    TYPING,
    RECORDING_AUDIO,
    IDLE,
    UNKNOWN
}

object ChatActivityKindSerializer :
    LenientEnumSerializer<ChatActivityKind>("ChatActivityKind", ChatActivityKind.entries, ChatActivityKind.UNKNOWN)

@Serializable(with = CallTypeSerializer::class)
enum class CallType {
    VOICE,
    VIDEO,
    UNKNOWN
}

object CallTypeSerializer : LenientEnumSerializer<CallType>("CallType", CallType.entries, CallType.UNKNOWN)

@Serializable(with = CallEndReasonSerializer::class)
enum class CallEndReason {
    ENDED,
    DECLINED,
    MISSED,
    BUSY,
    FAILED,
    UNKNOWN
}

object CallEndReasonSerializer :
    LenientEnumSerializer<CallEndReason>("CallEndReason", CallEndReason.entries, CallEndReason.UNKNOWN)

/** Where a call record ended up. Names match the backend domain `CallStatus` one for one. */
@Serializable(with = CallStatusSerializer::class)
enum class CallStatus {
    INITIATED,
    ANSWERED,
    ENDED,
    DECLINED,
    MISSED,
    UNKNOWN
}

object CallStatusSerializer : LenientEnumSerializer<CallStatus>("CallStatus", CallStatus.entries, CallStatus.UNKNOWN)

/** `message.reaction`'s verb. Lowercase on the wire, as it has always been. */
@Serializable(with = ReactionActionSerializer::class)
enum class ReactionAction(val wireName: String) {
    ADD("add"),
    REMOVE("remove"),
    UNKNOWN("unknown")
}

object ReactionActionSerializer : LenientEnumSerializer<ReactionAction>(
    "ReactionAction", ReactionAction.entries, ReactionAction.UNKNOWN, { it.wireName }
)

/**
 * Who may see a profile field. Lowercase on the wire — `UpdatePrivacyRequest` has always carried
 * `"everyone"`/`"contacts"`/`"nobody"`, and those strings are the contract.
 */
@Serializable(with = VisibilityLevelSerializer::class)
enum class VisibilityLevel(val wireName: String) {
    EVERYONE("everyone"),
    CONTACTS("contacts"),
    NOBODY("nobody"),
    UNKNOWN("unknown")
}

object VisibilityLevelSerializer : LenientEnumSerializer<VisibilityLevel>(
    "VisibilityLevel", VisibilityLevel.entries, VisibilityLevel.UNKNOWN, { it.wireName }
)

/** Includes [GRADIENT], which the backend has stored since #380 and this enum could not express (#877). */
@Serializable(with = WallpaperTypeSerializer::class)
enum class WallpaperType { DEFAULT, SOLID, GRADIENT, CUSTOM, UNKNOWN }

object WallpaperTypeSerializer :
    LenientEnumSerializer<WallpaperType>("WallpaperType", WallpaperType.entries, WallpaperType.UNKNOWN)

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
data class UserProfile(
    val id: String,
    // Nullable: only the caller's own profile (GET /users/me) exposes the phone number.
    // Public lookups (GET /users/{id}) return null to prevent phone-number harvesting (KVKK).
    val phoneNumber: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val about: String? = null,
    val isOnline: Boolean = false,
    val lastSeenAt: Instant? = null
)

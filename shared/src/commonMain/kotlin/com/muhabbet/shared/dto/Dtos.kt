package com.muhabbet.shared.dto

import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.MemberRole
import kotlinx.serialization.Serializable

// ─── API Envelope ────────────────────────────────────────

@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val error: ApiError? = null,
    val timestamp: String
)

@Serializable
data class ApiError(
    val code: String,
    val message: String
)

// ─── Auth DTOs ───────────────────────────────────────────

@Serializable
data class RequestOtpRequest(
    val phoneNumber: String             // E.164 format: +905XXXXXXXXX
)

@Serializable
data class RequestOtpResponse(
    val ttlSeconds: Int,                // OTP validity duration
    val retryAfterSeconds: Int,         // cooldown before next request
    val mockCode: String? = null        // OTP code returned only in mock/dev mode
)

@Serializable
data class VerifyOtpRequest(
    val phoneNumber: String,
    val otp: String,
    val deviceName: String,
    val platform: String                // "android" or "ios"
)

@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,                // seconds until access token expires
    val userId: String,
    val deviceId: String,
    val isNewUser: Boolean
)

@Serializable
data class FirebaseVerifyRequest(
    val idToken: String,
    val deviceName: String,
    val platform: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

// ─── User DTOs ───────────────────────────────────────────

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val about: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class ContactSyncRequest(
    val phoneHashes: List<String>       // SHA-256 hashes of phone numbers
)

@Serializable
data class ContactSyncResponse(
    val matchedContacts: List<MatchedContact>
)

@Serializable
data class MatchedContact(
    val userId: String,
    val phoneHash: String,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

// ─── Device DTOs ─────────────────────────────────────────

@Serializable
data class RegisterPushTokenRequest(
    val pushToken: String,
    /**
     * BCP-47 tag for the language this device wants **push notifications** in, e.g. "tr", "en".
     *
     * Push text is composed on the server, so the app's own locale never reaches it — this field is
     * the only way the reader's language is known when the notification is written (#469). Defaults
     * to null so an older build keeps registering exactly as before; the server then leaves the
     * device on whatever it last reported, and on Turkish if it never reported anything.
     */
    val locale: String? = null
)

// ─── Conversation DTOs ───────────────────────────────────

@Serializable
data class CreateConversationRequest(
    val type: ConversationType,
    val participantIds: List<String>,    // for DIRECT: exactly 1 other user
    val name: String? = null,           // for GROUP only
)

@Serializable
data class ConversationResponse(
    val id: String,
    val type: ConversationType,
    val name: String? = null,
    val avatarUrl: String? = null,
    val participants: List<ParticipantResponse>,
    val lastMessagePreview: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int,
    val createdAt: String,
    val disappearAfterSeconds: Int? = null,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val isLocked: Boolean = false,
    val announcementOnly: Boolean = false,
    val inviteLink: String? = null
)

@Serializable
data class ParticipantResponse(
    val userId: String,
    val displayName: String? = null,
    val phoneNumber: String? = null,
    val avatarUrl: String? = null,
    val role: MemberRole,
    val isOnline: Boolean
)

// ─── Group Management DTOs ───────────────────────────────

@Serializable
data class AddMembersRequest(
    val userIds: List<String>
)

/**
 * Every field is "leave alone" when null, so a caller changing only the photo does not have to
 * resend the name — and cannot blank it by omission.
 */
@Serializable
data class UpdateGroupRequest(
    val name: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class UpdateRoleRequest(
    val role: MemberRole
)

/**
 * Body of `PUT /api/v1/conversations/{id}/announcement`.
 *
 * Shared rather than declared privately on each side, which is the whole point of it existing
 * (#509). The group screen used to PATCH `{"announcementOnly": true}` at the *update-group* route,
 * whose body is [UpdateGroupRequest] and has no such field; `ignoreUnknownKeys` dropped it and the
 * server answered 200. A switch that turns a group read-only appeared to work and did not, and the
 * failure was in the permissive direction — nobody finds out until the group is spammed. With one
 * declaration compiled into both halves, that particular drift cannot recur silently.
 */
@Serializable
data class SetAnnouncementModeRequest(
    val enabled: Boolean
)

/**
 * What the server actually stored, echoed back so the switch renders server truth rather than the
 * caller's optimistic guess. The old client flipped before the request and reverted on throw; a
 * permission control has to be able to say "on" only because the server says so.
 */
@Serializable
data class AnnouncementModeResponse(
    val announcementOnly: Boolean
)

// ─── Message Management DTOs ─────────────────────────────

/**
 * Sends one text message over REST instead of over the WebSocket.
 *
 * The socket remains the normal path: it is already open while a chat is on screen and it carries
 * the ack back. This exists for the callers that have no socket and cannot afford to open one —
 * today that is the notification inline-reply `BroadcastReceiver`, which may run in a process that
 * was started for the broadcast alone and will be torn down as soon as it returns (#510).
 *
 * [messageId] is the same client-generated UUID the socket sends, and carries the same meaning: the
 * server rejects a second message with an id it has already stored, so a retry cannot double-post.
 */
@Serializable
data class SendMessageRequest(
    val messageId: String,
    val content: String
)

@Serializable
data class EditMessageRequest(
    val content: String
)

// ─── Media DTOs ──────────────────────────────────────────

@Serializable
data class MediaUploadResponse(
    val mediaId: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val contentType: String,
    val sizeBytes: Long,
    val durationSeconds: Int? = null
)

// ─── Link Preview ───────────────────────────────────────

@Serializable
data class LinkPreviewResponse(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null
)

// ─── Location DTOs ──────────────────────────────────────

@Serializable
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null
)

// ─── Poll DTOs ──────────────────────────────────────────

@Serializable
data class PollData(
    val question: String,
    val options: List<String>
)

@Serializable
data class PollVoteRequest(
    val optionIndex: Int
)

@Serializable
data class PollResultResponse(
    val messageId: String,
    val votes: List<PollOptionResult>,
    val totalVotes: Int,
    val myVote: Int? = null
)

@Serializable
data class PollOptionResult(
    val index: Int,
    val text: String,
    val count: Int
)

// ─── Status/Stories DTOs ─────────────────────────────────

@Serializable
data class StatusCreateRequest(
    val content: String? = null,
    val mediaUrl: String? = null
)

@Serializable
data class StatusResponse(
    val id: String,
    val userId: String,
    val content: String? = null,
    val mediaUrl: String? = null,
    val createdAt: Long,
    val expiresAt: Long
)

@Serializable
data class UserStatusGroup(
    val userId: String,
    val statuses: List<StatusResponse>,
    /**
     * The author's name and avatar, resolved server-side. Present so the client never has to
     * invent a label from [userId] — rendering its first eight characters produced the hex string
     * reported as a phone hash in #507.
     */
    val displayName: String? = null,
    val avatarUrl: String? = null
)

// ─── Channel DTOs ───────────────────────────────────────

@Serializable
data class ChannelInfoResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    val subscriberCount: Int,
    val isSubscribed: Boolean,
    val createdAt: String
)

// ─── Reaction DTOs ─────────────────────────────────────

@Serializable
data class ReactionRequest(
    val emoji: String
)

@Serializable
data class ReactionResponse(
    val emoji: String,
    val count: Int,
    val userIds: List<String>
)

// ─── Encryption DTOs ───────────────────────────────────────

@Serializable
data class RegisterKeyBundleRequest(
    val identityKey: String,
    val signedPreKey: String,
    val signedPreKeySignature: String,
    val signedPreKeyId: Int,
    val registrationId: Int
)

@Serializable
data class UploadPreKeysRequest(
    val preKeys: List<PreKeyDto>
)

@Serializable
data class PreKeyDto(
    val keyId: Int,
    val publicKey: String
)

@Serializable
data class PreKeyBundleResponse(
    val identityKey: String,
    val signedPreKey: String,
    val signedPreKeySignature: String? = null,
    val signedPreKeyId: Int,
    val registrationId: Int,
    val oneTimePreKey: String? = null,
    val oneTimePreKeyId: Int? = null
)

// ─── Call DTOs ──────────────────────────────────────────

@Serializable
data class CallHistoryResponse(
    val id: String,
    val callId: String,
    val callerId: String,
    val calleeId: String,
    val callerName: String? = null,
    val calleeName: String? = null,
    val callType: String,           // VOICE or VIDEO
    val status: String,             // INITIATED, ANSWERED, ENDED, DECLINED, MISSED
    val startedAt: String,
    val answeredAt: String? = null,
    val endedAt: String? = null,
    val durationSeconds: Int? = null
)

// ─── User Profile Detail ────────────────────────────────

@Serializable
data class UserProfileDetailResponse(
    val id: String,
    // Nullable: never populated for foreign-user lookups — phone numbers stay private (KVKK P0-9).
    val phoneNumber: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val about: String? = null,
    val isOnline: Boolean = false,
    val lastSeenAt: String? = null,
    val mutualGroups: List<MutualGroupResponse> = emptyList(),
    val sharedMediaCount: Int = 0
)

@Serializable
data class MutualGroupResponse(
    val conversationId: String,
    val name: String,
    val avatarUrl: String? = null,
    val memberCount: Int
)

// ─── Message Info ────────────────────────────────────────

@Serializable
data class MessageInfoResponse(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val contentType: String,
    val sentAt: String,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val recipients: List<RecipientDeliveryInfo>
)

@Serializable
data class RecipientDeliveryInfo(
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val status: String,
    val updatedAt: String? = null
)

// ─── Storage Usage ───────────────────────────────────────

@Serializable
data class StorageUsageResponse(
    val totalBytes: Long,
    val imageBytes: Long,
    val audioBytes: Long,
    val documentBytes: Long,
    val imageCount: Int,
    val audioCount: Int,
    val documentCount: Int
)

// ─── Pagination ──────────────────────────────────────────

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val nextCursor: String?,            // null = no more pages
    val hasMore: Boolean
)

// ─── Two-Step Verification DTOs ─────────────────────────
// Both sides use these. The backend used to declare its own private copies in
// TwoStepVerificationController, which is how the client came to POST a body the server never
// served (#544) — two declarations cannot disagree if there is only one.
@Serializable
data class SetupTwoStepRequest(val pin: String, val email: String? = null)

@Serializable
data class VerifyTwoStepRequest(val pin: String)

/**
 * Turning two-step verification off requires the PIN that turned it on.
 *
 * The client sent no body at all, so `DELETE /api/v1/auth/two-step` answered 400 for every caller
 * and two-step could never be switched off once on (#544).
 */
@Serializable
data class DisableTwoStepRequest(val currentPin: String)

@Serializable
data class TwoStepStatusResponse(val enabled: Boolean, val hasEmail: Boolean = false)

// ─── Archive/Mute DTOs ──────────────────────────────────
@Serializable
data class MuteRequest(val duration: String)  // "8h", "1w", "always"

// ─── Invite Link DTOs ───────────────────────────────────
@Serializable
data class CreateInviteLinkRequest(
    val requiresApproval: Boolean = false,
    val maxUses: Int? = null,
    val expiresInHours: Int? = null
)

@Serializable
data class InviteLinkResponse(
    val id: String,
    val conversationId: String,
    val inviteToken: String,
    val inviteUrl: String,
    val requiresApproval: Boolean,
    val isActive: Boolean,
    val maxUses: Int? = null,
    val useCount: Int,
    val expiresAt: String? = null,
    val createdAt: String
)

@Serializable
data class JoinViaLinkRequest(val token: String)

// ─── Join Request DTOs ──────────────────────────────────
@Serializable
data class JoinRequestResponse(
    val id: String,
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val status: String,
    val createdAt: String
)

// ─── Community DTOs ─────────────────────────────────────
@Serializable
data class CreateCommunityRequest(
    val name: String,
    val description: String? = null
)

@Serializable
data class CommunityResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val groupCount: Int = 0,
    val memberCount: Int = 0,
    val createdAt: String
)

@Serializable
data class CommunityDetailResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val groups: List<CommunityGroupInfo>,
    val memberCount: Int = 0,
    val myRole: String? = null,
    val createdAt: String,
    /**
     * The community's announcement channel (#584) — a GROUP conversation every member is enrolled
     * in, where only admins/owners may post. Open it the same way a [CommunityGroupInfo] is opened:
     * `ChatTarget(conversationId = announcementGroupId, isGroup = true)`. Nullable on the wire for
     * an old server that predates this field, not because a real community lacks one — the server
     * creates it the moment a community exists and backfills it lazily for any that predate that.
     */
    val announcementGroupId: String? = null
)

@Serializable
data class CommunityGroupInfo(
    val conversationId: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val memberCount: Int
)

@Serializable
data class UpdateCommunityRequest(
    val name: String,
    val description: String? = null
)

/**
 * One row of `GET /api/v1/communities/{id}/members`.
 *
 * [role] is the raw `MemberRole` name rather than the enum, for the same reason
 * [CommunityDetailResponse.myRole] is: a role added on the server must not fail the whole decode on
 * an app that has not shipped yet.
 */
@Serializable
data class CommunityMemberResponse(
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val role: String,
    val joinedAt: String
)

/**
 * Someone the caller is allowed to enrol right now, from
 * `GET /api/v1/communities/{id}/member-candidates`.
 *
 * The server computes this from the same rule `addMember` enforces — membership of one of the
 * community's own groups (#375) — so the picker cannot offer a person the add would then reject.
 * It is deliberately not a user search: until the invite flow (#387) exists, nobody outside those
 * groups can be added at all.
 */
@Serializable
data class CommunityMemberCandidateResponse(
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

// ─── Group Event DTOs ───────────────────────────────────
@Serializable
data class CreateGroupEventRequest(
    val title: String,
    val description: String? = null,
    val eventTime: Long,  // epoch millis
    val location: String? = null
)

@Serializable
data class GroupEventResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    val eventTime: Long,
    val location: String? = null,
    val createdBy: String,
    val goingCount: Int,
    val createdAt: String
)

@Serializable
data class RsvpRequest(val status: String)  // GOING, NOT_GOING, MAYBE

// ─── View-Once DTOs ─────────────────────────────────────

/**
 * The one-time release of a view-once message's media.
 *
 * Returned by `POST /api/v1/messages/{messageId}/view-once`, which is the **only** response in the
 * API that carries a view-once blob URL — every list, search, media-grid and socket payload nulls
 * it. The call burns the message in the same transaction that reads it, so a second caller (or a
 * second tap) gets `MSG_VIEW_ONCE_ALREADY_VIEWED` and no URL.
 *
 * Replaced a `ViewOnceStatusResponse` that had no producer and no consumer.
 */
@Serializable
data class ViewOnceRevealResponse(
    val messageId: String,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val viewedAt: Long
)

// ─── Wallpaper DTOs ─────────────────────────────────────
/**
 * These are the wire contract for `/api/v1/wallpapers`, and until #380 they were not.
 *
 * `ChatWallpaperController` declared its own pair with the same class names and different field
 * names — `type` and `value` against these `wallpaperType` and `wallpaperValue` — and its `type`
 * carried a `"DEFAULT"` default. A client serialising the classes here would therefore have sent
 * fields the server did not read, and the server would have filled in DEFAULT and wiped the
 * wallpaper the request was sent to set. Nothing caught it because nothing had ever called the
 * endpoint. The controller now uses these, so the mismatch cannot come back silently.
 *
 * `wallpaperType` has no default for the same reason: a missing field must fail the request, not
 * quietly mean "reset it".
 */
@Serializable
data class SetWallpaperRequest(
    val wallpaperType: String,  // DEFAULT, SOLID, GRADIENT, CUSTOM
    val wallpaperValue: String? = null,
    val darkModeValue: String? = null
)

/**
 * [id] is what `DELETE /api/v1/wallpapers/{id}` takes, and [conversationId] is how a caller tells the
 * global default (null) from a per-conversation override. Both were on the controller's private copy
 * and missing here, which would have left a client able to create wallpapers it could not then remove.
 */
@Serializable
data class WallpaperResponse(
    val id: String,
    val conversationId: String? = null,
    val wallpaperType: String,
    val wallpaperValue: String? = null,
    val darkModeValue: String? = null,
    val createdAt: String
)

// ─── Privacy Settings DTOs ──────────────────────────────
/**
 * Every field here is backed by a column on `users` and honoured by a reader on the server. Do not
 * add one that is not: `PrivacySettingsResponse` used to advertise `lastSeenVisibility` and
 * `profilePhotoVisibility`, neither of which existed in the schema, the request DTO or any query —
 * and the controller quietly answered with a three-field copy of its own rather than this class.
 *
 * `onlineStatusVisibility` **is** the last-seen control; it gates presence and last-seen together
 * (see `UserController.resolveVisibility`). It is not a separate setting, which is why the screen
 * labels it "last seen" and sends it under this name.
 *
 * Values for the visibility fields: `everyone`, `contacts`, `nobody`.
 */
@Serializable
data class UpdatePrivacyRequest(
    val readReceiptsEnabled: Boolean? = null,
    val onlineStatusVisibility: String? = null,
    val aboutVisibility: String? = null
)

@Serializable
data class PrivacySettingsResponse(
    val readReceiptsEnabled: Boolean,
    val onlineStatusVisibility: String,
    val aboutVisibility: String
)

// ─── Moderation DTOs ────────────────────────────────────
/**
 * `reason` is a raw string rather than a shared enum, deliberately: it must decode against the
 * backend domain's `ReportReason` names (`SPAM`, `HARASSMENT`, `ILLEGAL_CONTENT`, `HATE_SPEECH`,
 * `OTHER`), and a mismatch there is a `VALIDATION_ERROR` the server already returns — duplicating
 * that enum here would just be a second place for the two to drift apart, the same trade already
 * made for `MemberRole`/`ContentType`/`ConversationType`.
 *
 * Exactly one of [reportedUserId], [reportedMessageId] and [reportedConversationId] is normally
 * set; the server does not require it, but a report with none of the three has nothing to review.
 */
@Serializable
data class CreateReportRequest(
    val reportedUserId: String? = null,
    val reportedMessageId: String? = null,
    val reportedConversationId: String? = null,
    val reason: String,
    val description: String? = null
)

/**
 * One row of the caller's own block list.
 *
 * `GET /api/v1/moderation/blocks` used to answer with bare `blockedUserIds` — a list of UUIDs and
 * nothing else — which is why no blocked-list screen was ever built: a UUID is not a name, and
 * `GET /users/{id}` strips a foreign user's phone number, so the client had no way to resolve one
 * even with N extra requests. [displayName] and [avatarUrl] are resolved server-side in one batched
 * query via the moderation module's own `UserDirectoryPort`, the same shape messaging already uses
 * to put a name on a message sender.
 */
@Serializable
data class BlockedUserResponse(
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val blockedAt: String
)

// ─── Broadcast List DTOs ────────────────────────────────
@Serializable
data class CreateBroadcastListRequest(
    val name: String,
    val memberIds: List<String>
)

@Serializable
data class BroadcastListResponse(
    val id: String,
    val name: String,
    val memberCount: Int = 0,
    val createdAt: String
)

/**
 * A recipient of a broadcast list, from `GET /api/v1/broadcast-lists/{id}/members`.
 *
 * [displayName] is nullable because a user who never set one is normal; the screen falls back to a
 * localized "unknown", never to the raw id.
 */
@Serializable
data class BroadcastMemberResponse(
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

// ─── Login Approval DTOs ────────────────────────────────
@Serializable
data class LoginApprovalNotification(
    val approvalId: String,
    val deviceName: String? = null,
    val platform: String? = null,
    val ipAddress: String? = null,
    val createdAt: Long
)

// ─── Multi-Device Linking DTOs (Tier 2, NON-CRYPTO slice) ───────────────────
// Additive & backwards-compatible. No private key material is ever carried here — only the QR
// link token and the companion's OPAQUE PUBLIC bundle (consumed later by the crypto slice).

/** Response to POST /devices/link/begin — what the primary renders inside the QR code. */
@Serializable
data class DeviceLinkBeginResponse(
    val sessionId: String,
    val linkToken: String,
    val expiresAt: String
)

/** Body of POST /devices/link/complete — sent by the companion after scanning the QR. */
@Serializable
data class DeviceLinkCompleteRequest(
    val linkToken: String,
    val platform: String,                 // web | desktop | android | ios
    val deviceName: String? = null,
    /** Opaque PUBLIC prekey bundle; null until the libsignal-backed crypto slice ships. */
    val publicBundle: String? = null
)

/** A linked device as shown on the management screen / returned by complete & revoke. */
@Serializable
data class LinkedDeviceResponse(
    val id: String,
    val platform: String,
    val displayName: String? = null,
    val isPrimary: Boolean,
    val isCompanion: Boolean,
    val linkedByDeviceId: String? = null,
    val lastActiveAt: String? = null,
    val createdAt: String,
    val revoked: Boolean = false
)

/**
 * The payload the primary encodes into the QR image. Kept tiny and JSON-serializable so the
 * companion's scanner can decode it directly. `v` lets us evolve the QR format additively.
 */
@Serializable
data class DeviceLinkQrPayload(
    val v: Int = 1,
    val linkToken: String,
    val apiBaseUrl: String
)

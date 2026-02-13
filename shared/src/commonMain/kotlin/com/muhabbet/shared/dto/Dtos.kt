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
    val displayName: String?,
    val avatarUrl: String?
)

// ─── Device DTOs ─────────────────────────────────────────

@Serializable
data class RegisterPushTokenRequest(
    val pushToken: String
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
    val name: String?,
    val avatarUrl: String?,
    val participants: List<ParticipantResponse>,
    val lastMessagePreview: String?,
    val lastMessageAt: String?,
    val unreadCount: Int,
    val createdAt: String,
    val disappearAfterSeconds: Int? = null,
    val isPinned: Boolean = false
)

@Serializable
data class ParticipantResponse(
    val userId: String,
    val displayName: String?,
    val phoneNumber: String? = null,
    val avatarUrl: String?,
    val role: MemberRole,
    val isOnline: Boolean
)

// ─── Group Management DTOs ───────────────────────────────

@Serializable
data class AddMembersRequest(
    val userIds: List<String>
)

@Serializable
data class UpdateGroupRequest(
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class UpdateRoleRequest(
    val role: MemberRole
)

// ─── Message Management DTOs ─────────────────────────────

@Serializable
data class EditMessageRequest(
    val content: String
)

// ─── Media DTOs ──────────────────────────────────────────

@Serializable
data class MediaUploadResponse(
    val mediaId: String,
    val url: String,
    val thumbnailUrl: String?,
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
    val statuses: List<StatusResponse>
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
    val phoneNumber: String,
    val displayName: String?,
    val avatarUrl: String?,
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
    val recipients: List<RecipientDeliveryInfo>
)

@Serializable
data class RecipientDeliveryInfo(
    val userId: String,
    val displayName: String?,
    val status: String,
    val updatedAt: String?
)

// ─── Pagination ──────────────────────────────────────────

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val nextCursor: String?,            // null = no more pages
    val hasMore: Boolean
)

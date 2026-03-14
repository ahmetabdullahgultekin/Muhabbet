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
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val recipients: List<RecipientDeliveryInfo>
)

@Serializable
data class RecipientDeliveryInfo(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String? = null,
    val status: String,
    val updatedAt: String?
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
@Serializable
data class SetupTwoStepRequest(val pin: String, val email: String? = null)

@Serializable
data class VerifyTwoStepRequest(val pin: String)

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
    val maxUses: Int?,
    val useCount: Int,
    val expiresAt: String?,
    val createdAt: String
)

@Serializable
data class JoinViaLinkRequest(val token: String)

// ─── Join Request DTOs ──────────────────────────────────
@Serializable
data class JoinRequestResponse(
    val id: String,
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
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
    val description: String?,
    val avatarUrl: String?,
    val groupCount: Int,
    val memberCount: Int,
    val createdAt: String
)

@Serializable
data class CommunityDetailResponse(
    val id: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val groups: List<CommunityGroupInfo>,
    val memberCount: Int,
    val myRole: String?,
    val createdAt: String
)

@Serializable
data class CommunityGroupInfo(
    val conversationId: String,
    val name: String?,
    val avatarUrl: String?,
    val memberCount: Int
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
    val description: String?,
    val eventTime: Long,
    val location: String?,
    val createdBy: String,
    val goingCount: Int,
    val createdAt: String
)

@Serializable
data class RsvpRequest(val status: String)  // GOING, NOT_GOING, MAYBE

// ─── View-Once DTOs ─────────────────────────────────────
@Serializable
data class ViewOnceStatusResponse(
    val messageId: String,
    val viewed: Boolean,
    val viewedAt: Long? = null
)

// ─── Wallpaper DTOs ─────────────────────────────────────
@Serializable
data class SetWallpaperRequest(
    val wallpaperType: String,  // DEFAULT, SOLID, CUSTOM
    val wallpaperValue: String? = null,
    val darkModeValue: String? = null
)

@Serializable
data class WallpaperResponse(
    val wallpaperType: String,
    val wallpaperValue: String?,
    val darkModeValue: String?
)

// ─── Privacy Settings DTOs ──────────────────────────────
@Serializable
data class UpdatePrivacyRequest(
    val readReceiptsEnabled: Boolean? = null,
    val onlineStatusVisibility: String? = null,  // everyone, contacts, nobody
    val aboutVisibility: String? = null
)

@Serializable
data class PrivacySettingsResponse(
    val readReceiptsEnabled: Boolean,
    val onlineStatusVisibility: String,
    val aboutVisibility: String,
    val lastSeenVisibility: String,
    val profilePhotoVisibility: String
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
    val memberCount: Int,
    val createdAt: String
)

// ─── Login Approval DTOs ────────────────────────────────
@Serializable
data class LoginApprovalNotification(
    val approvalId: String,
    val deviceName: String?,
    val platform: String?,
    val ipAddress: String?,
    val createdAt: Long
)

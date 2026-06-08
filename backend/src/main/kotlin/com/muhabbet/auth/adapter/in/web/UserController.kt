package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.PresenceVisibilityPolicy
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.MutualGroupResponse
import com.muhabbet.shared.dto.UpdateProfileRequest
import com.muhabbet.shared.dto.UserProfileDetailResponse
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.UserProfile
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.security.InputSanitizer
import com.muhabbet.shared.validation.ValidationRules
import com.muhabbet.shared.web.ApiResponseBuilder
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userRepository: UserRepository,
    private val presencePort: PresencePort,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository
) {

    @GetMapping("/{userId}")
    fun getUserById(@PathVariable userId: UUID): ResponseEntity<ApiResponse<UserProfile>> {
        val requesterId = AuthenticatedUser.currentUserId()
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val presence = resolvePresenceVisibility(user, requesterId)

        return ApiResponseBuilder.ok(
            UserProfile(
                id = user.id.toString(),
                // Phone number is NOT exposed on foreign-user lookups (KVKK P0-9); only GET /me returns it.
                phoneNumber = null,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                about = user.about,
                isOnline = presence.isOnline,
                lastSeenAt = presence.lastSeen
            )
        )
    }

    private data class PresenceView(
        val isOnline: Boolean,
        val lastSeen: kotlinx.datetime.Instant?
    )

    /**
     * Applies the target user's onlineStatusVisibility to their presence/last-seen.
     * - "everyone": visible to any authenticated caller
     * - "contacts": visible only to users who share a conversation with the target
     * - "nobody": hidden from everyone except the user themselves
     * The user always sees their own presence.
     */
    private fun resolvePresenceVisibility(
        user: com.muhabbet.auth.domain.model.User,
        requesterId: UUID
    ): PresenceView {
        // Shared single source of truth with the realtime WS path (PresenceVisibilityPolicy). Contacts
        // are fetched lazily — only the "contacts" rule needs them, so "everyone"/"nobody" skip the query.
        val contactIds = if (user.onlineStatusVisibility.lowercase() == "contacts" && requesterId != user.id) {
            conversationRepository.findAllContactUserIds(user.id)
        } else {
            emptySet()
        }
        val visible = PresenceVisibilityPolicy.isVisibleTo(
            visibility = user.onlineStatusVisibility,
            subjectId = user.id,
            requesterId = requesterId,
            contactIds = contactIds
        )
        if (!visible) return PresenceView(isOnline = false, lastSeen = null)

        val lastSeen = user.lastSeenAt?.let {
            kotlinx.datetime.Instant.fromEpochSeconds(it.epochSecond, it.nano.toLong())
        }
        return PresenceView(isOnline = presencePort.isOnline(user.id), lastSeen = lastSeen)
    }

    @GetMapping("/{userId}/detail")
    fun getUserDetail(@PathVariable userId: UUID): ResponseEntity<ApiResponse<UserProfileDetailResponse>> {
        val currentUserId = AuthenticatedUser.currentUserId()
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val presence = resolvePresenceVisibility(user, currentUserId)

        // Find mutual groups: conversations where both users are members and type is GROUP
        val myConversations = conversationRepository.findConversationsByUserId(currentUserId)
        val targetConversations = conversationRepository.findConversationsByUserId(userId)
        val targetConvIds = targetConversations.map { it.id }.toSet()

        val mutualGroups = myConversations
            .filter { it.id in targetConvIds && it.type == ConversationType.GROUP }
            .map { conv ->
                val members = conversationRepository.findMembersByConversationId(conv.id)
                MutualGroupResponse(
                    conversationId = conv.id.toString(),
                    name = conv.name ?: "",
                    avatarUrl = conv.avatarUrl,
                    memberCount = members.size
                )
            }

        // Count shared media: messages with mediaUrl in DM conversations between the two users
        val sharedConvIds = myConversations
            .filter { it.id in targetConvIds && it.type == ConversationType.DIRECT }
            .map { it.id }
        val sharedMediaCount = sharedConvIds.sumOf { convId ->
            messageRepository.countMediaInConversation(convId)
        }

        return ApiResponseBuilder.ok(
            UserProfileDetailResponse(
                id = user.id.toString(),
                // Phone number is NOT exposed on foreign-user lookups (KVKK P0-9).
                phoneNumber = null,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                about = user.about,
                isOnline = presence.isOnline,
                lastSeenAt = presence.lastSeen?.toString(),
                mutualGroups = mutualGroups,
                sharedMediaCount = sharedMediaCount
            )
        )
    }

    @GetMapping("/me")
    fun getMe(): ResponseEntity<ApiResponse<UserProfile>> {
        val userId = AuthenticatedUser.currentUserId()
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED)

        return ApiResponseBuilder.ok(
            UserProfile(
                id = user.id.toString(),
                phoneNumber = user.phoneNumber,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                about = user.about
            )
        )
    }

    @PatchMapping("/me/privacy")
    fun updatePrivacy(@RequestBody request: UpdatePrivacyRequest): ResponseEntity<ApiResponse<PrivacySettingsResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED)

        val updated = userRepository.save(
            user.copy(
                readReceiptsEnabled = request.readReceiptsEnabled ?: user.readReceiptsEnabled,
                onlineStatusVisibility = request.onlineStatusVisibility ?: user.onlineStatusVisibility,
                aboutVisibility = request.aboutVisibility ?: user.aboutVisibility,
                updatedAt = java.time.Instant.now()
            )
        )

        return ApiResponseBuilder.ok(
            PrivacySettingsResponse(
                readReceiptsEnabled = updated.readReceiptsEnabled,
                onlineStatusVisibility = updated.onlineStatusVisibility,
                aboutVisibility = updated.aboutVisibility
            )
        )
    }

    @PatchMapping("/me")
    fun updateMe(@RequestBody request: UpdateProfileRequest): ResponseEntity<ApiResponse<UserProfile>> {
        val userId = AuthenticatedUser.currentUserId()
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED)

        // Input normalization at the boundary (strip control/zero-width/RTL-override
        // chars, trim, clamp) BEFORE validation, so an all-invisible value normalizes
        // to blank and is rejected. Normalization only — NOT HTML-escaping; the mobile
        // client renders these as plain text, so escaping would corrupt "Tom & Jerry".
        // Clamp at the hard ceiling, not the field limit, so isValidDisplayName /
        // isValidAbout stay authoritative and reject over-length input (no silent
        // truncation).
        val normalizedDisplayName = request.displayName
            ?.let { InputSanitizer.normalizeText(it, ValidationRules.INPUT_HARD_CAP) }
        val normalizedAbout = request.about
            ?.let { InputSanitizer.normalizeText(it, ValidationRules.INPUT_HARD_CAP) }

        // Validate inputs
        normalizedDisplayName?.let {
            if (!ValidationRules.isValidDisplayName(it)) {
                throw BusinessException(ErrorCode.VALIDATION_ERROR, "Geçersiz görünen ad")
            }
        }
        normalizedAbout?.let {
            if (!ValidationRules.isValidAbout(it)) {
                throw BusinessException(ErrorCode.VALIDATION_ERROR, "Hakkımda metni çok uzun")
            }
        }

        val updated = userRepository.save(
            user.copy(
                displayName = normalizedDisplayName ?: user.displayName,
                about = normalizedAbout ?: user.about,
                avatarUrl = request.avatarUrl ?: user.avatarUrl
            )
        )

        return ApiResponseBuilder.ok(
            UserProfile(
                id = updated.id.toString(),
                phoneNumber = updated.phoneNumber,
                displayName = updated.displayName,
                avatarUrl = updated.avatarUrl,
                about = updated.about
            )
        )
    }
}

data class UpdatePrivacyRequest(
    val readReceiptsEnabled: Boolean? = null,
    val onlineStatusVisibility: String? = null,
    val aboutVisibility: String? = null
)

data class PrivacySettingsResponse(
    val readReceiptsEnabled: Boolean,
    val onlineStatusVisibility: String,
    val aboutVisibility: String
)

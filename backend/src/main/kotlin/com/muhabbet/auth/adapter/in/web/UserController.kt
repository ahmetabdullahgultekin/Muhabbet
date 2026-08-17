package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.BlockDirectoryPort
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.MutualGroupResponse
import com.muhabbet.shared.dto.PrivacySettingsResponse
import com.muhabbet.shared.dto.UpdatePrivacyRequest
import com.muhabbet.shared.dto.UpdateProfileRequest
import com.muhabbet.shared.dto.UserProfileDetailResponse
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.UserProfile
import com.muhabbet.shared.security.AuthenticatedUser
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
    private val messageRepository: MessageRepository,
    private val blockDirectory: BlockDirectoryPort
) {

    @GetMapping("/{userId}")
    fun getUserById(@PathVariable userId: UUID): ResponseEntity<ApiResponse<UserProfile>> {
        val requesterId = AuthenticatedUser.currentUserId()
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val visible = resolveVisibility(user, requesterId)

        return ApiResponseBuilder.ok(
            UserProfile(
                id = user.id.toString(),
                // Phone number is NOT exposed on foreign-user lookups (KVKK P0-9); only GET /me returns it.
                phoneNumber = null,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                about = visible.about,
                isOnline = visible.isOnline,
                lastSeenAt = visible.lastSeen
            )
        )
    }

    private data class VisibleProfile(
        val isOnline: Boolean,
        val lastSeen: kotlinx.datetime.Instant?,
        val about: String?
    )

    /**
     * Applies the target user's own privacy settings to the slice of their profile this caller may
     * see. `onlineStatusVisibility` gates presence and last-seen; `aboutVisibility` gates the about
     * text. Both speak the same everyone/contacts/nobody vocabulary:
     * - "everyone": visible to any authenticated caller
     * - "contacts": visible only to users who share a conversation with the target
     * - "nobody": hidden from everyone except the user themselves
     *
     * `aboutVisibility` was stored and never consulted before — the column was written by
     * `PATCH /me/privacy` and every lookup returned `about` regardless, so the setting was a no-op
     * end to end. Resolved together with presence so the "contacts" case costs one membership
     * query rather than one per field.
     *
     * A block short-circuits all of it. Whatever the target chose for everyone else, someone they
     * blocked sees no presence, no last seen and no about — a blocked harasser watching a green dot
     * to learn when their target is awake is the concrete harm here. Name and avatar still show,
     * because a chat the two shared before the block would otherwise become an anonymous row.
     */
    private fun resolveVisibility(user: User, requesterId: UUID): VisibleProfile {
        if (requesterId != user.id && blockDirectory.hasBlocked(user.id, requesterId)) {
            return VisibleProfile(isOnline = false, lastSeen = null, about = null)
        }

        val contactIds: Set<UUID> by lazy { conversationRepository.findAllContactUserIds(user.id) }

        fun allows(visibility: String): Boolean = when (visibility.lowercase()) {
            "everyone" -> true
            "nobody" -> requesterId == user.id
            "contacts" -> requesterId == user.id || requesterId in contactIds
            else -> false
        }

        val presenceVisible = allows(user.onlineStatusVisibility)
        return VisibleProfile(
            isOnline = presenceVisible && presencePort.isOnline(user.id),
            lastSeen = user.lastSeenAt
                ?.takeIf { presenceVisible }
                ?.let { kotlinx.datetime.Instant.fromEpochSeconds(it.epochSecond, it.nano.toLong()) },
            about = user.about?.takeIf { allows(user.aboutVisibility) }
        )
    }

    @GetMapping("/{userId}/detail")
    fun getUserDetail(@PathVariable userId: UUID): ResponseEntity<ApiResponse<UserProfileDetailResponse>> {
        val currentUserId = AuthenticatedUser.currentUserId()
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val visible = resolveVisibility(user, currentUserId)

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
                about = visible.about,
                isOnline = visible.isOnline,
                lastSeenAt = visible.lastSeen?.toString(),
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

    /**
     * The read side of `PATCH /me/privacy`, which shipped without one.
     *
     * A settings screen with no way to fetch the stored values can only guess them, and the guess
     * was the most permissive option in each case — so a user who had restricted something saw
     * "everyone" the next time they opened the screen, and re-saving silently widened it again.
     */
    @GetMapping("/me/privacy")
    fun getPrivacy(): ResponseEntity<ApiResponse<PrivacySettingsResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED)

        return ApiResponseBuilder.ok(
            PrivacySettingsResponse(
                readReceiptsEnabled = user.readReceiptsEnabled,
                onlineStatusVisibility = user.onlineStatusVisibility,
                aboutVisibility = user.aboutVisibility
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

        // Validate inputs
        request.displayName?.let {
            if (!ValidationRules.isValidDisplayName(it)) {
                throw BusinessException(ErrorCode.VALIDATION_ERROR, "Geçersiz görünen ad")
            }
        }
        request.about?.let {
            if (!ValidationRules.isValidAbout(it)) {
                throw BusinessException(ErrorCode.VALIDATION_ERROR, "Hakkımda metni çok uzun")
            }
        }

        val updated = userRepository.save(
            user.copy(
                displayName = request.displayName ?: user.displayName,
                about = request.about ?: user.about,
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

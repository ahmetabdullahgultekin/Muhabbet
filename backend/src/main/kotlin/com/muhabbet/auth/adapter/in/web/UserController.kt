package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.MutualGroupResponse
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
    private val messageRepository: MessageRepository
) {

    @GetMapping("/{userId}")
    fun getUserById(@PathVariable userId: UUID): ResponseEntity<ApiResponse<UserProfile>> {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Kullanıcı bulunamadı")

        val isOnline = presencePort.isOnline(userId)
        val lastSeen = user.lastSeenAt?.let {
            kotlinx.datetime.Instant.fromEpochSeconds(it.epochSecond, it.nano.toLong())
        }

        return ApiResponseBuilder.ok(
            UserProfile(
                id = user.id.toString(),
                phoneNumber = user.phoneNumber,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                about = user.about,
                isOnline = isOnline,
                lastSeenAt = lastSeen
            )
        )
    }

    @GetMapping("/{userId}/detail")
    fun getUserDetail(@PathVariable userId: UUID): ResponseEntity<ApiResponse<UserProfileDetailResponse>> {
        val currentUserId = AuthenticatedUser.currentUserId()
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val isOnline = presencePort.isOnline(userId)
        val lastSeen = user.lastSeenAt?.let {
            kotlinx.datetime.Instant.fromEpochSeconds(it.epochSecond, it.nano.toLong())
        }

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
                phoneNumber = user.phoneNumber,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                about = user.about,
                isOnline = isOnline,
                lastSeenAt = lastSeen?.toString(),
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

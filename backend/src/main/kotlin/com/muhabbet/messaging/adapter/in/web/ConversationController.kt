package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.messaging.domain.port.`in`.GetConversationsUseCase
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.CreateConversationRequest
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.MemberRole as SharedMemberRole
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/conversations")
class ConversationController(
    private val createConversationUseCase: CreateConversationUseCase,
    private val getConversationsUseCase: GetConversationsUseCase,
    private val manageGroupUseCase: com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase,
    private val conversationRepository: com.muhabbet.messaging.domain.port.out.ConversationRepository,
    private val userRepository: UserRepository,
    private val presencePort: PresencePort
) {

    @PostMapping
    fun createConversation(@RequestBody request: CreateConversationRequest): ResponseEntity<ApiResponse<ConversationResponse>> {
        val userId = AuthenticatedUser.currentUserId()

        val type = when (request.type) {
            com.muhabbet.shared.model.ConversationType.DIRECT -> ConversationType.DIRECT
            com.muhabbet.shared.model.ConversationType.GROUP -> ConversationType.GROUP
            com.muhabbet.shared.model.ConversationType.CHANNEL -> ConversationType.CHANNEL
        }

        val result = createConversationUseCase.createConversation(
            type = type,
            creatorId = userId,
            participantIds = request.participantIds.map { UUID.fromString(it) },
            name = request.name
        )

        val memberUserIds = result.members.map { it.userId }
        val usersMap = userRepository.findAllByIds(memberUserIds).associateBy { it.id }
        val onlineIds = presencePort.getOnlineUserIds(memberUserIds)

        val response = ConversationResponse(
            id = result.conversation.id.toString(),
            type = request.type,
            name = result.conversation.name,
            avatarUrl = result.conversation.avatarUrl,
            participants = result.members.map { m ->
                val user = usersMap[m.userId]
                ParticipantResponse(
                    userId = m.userId.toString(),
                    displayName = user?.displayName,
                    phoneNumber = user?.phoneNumber,
                    avatarUrl = user?.avatarUrl,
                    role = SharedMemberRole.valueOf(m.role.name),
                    isOnline = m.userId in onlineIds
                )
            },
            lastMessagePreview = null,
            lastMessageAt = null,
            unreadCount = 0,
            createdAt = result.conversation.createdAt.toString(),
            disappearAfterSeconds = result.conversation.disappearAfterSeconds
        )

        return ApiResponseBuilder.created(response)
    }

    @GetMapping
    fun getConversations(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ApiResponse<PaginatedResponse<ConversationResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val page = getConversationsUseCase.getConversations(userId, cursor, limit)

        val allParticipantIds = page.items.flatMap { it.participantIds }.distinct()
        val usersMap = userRepository.findAllByIds(allParticipantIds).associateBy { it.id }
        val onlineIds = presencePort.getOnlineUserIds(allParticipantIds)

        val items = page.items.map { summary ->
            ConversationResponse(
                id = summary.conversationId.toString(),
                type = com.muhabbet.shared.model.ConversationType.valueOf(summary.type.uppercase()),
                name = summary.name,
                avatarUrl = summary.avatarUrl,
                participants = summary.participantIds.map { pid ->
                    val user = usersMap[pid]
                    ParticipantResponse(
                        userId = pid.toString(),
                        displayName = user?.displayName,
                        phoneNumber = user?.phoneNumber,
                        avatarUrl = user?.avatarUrl,
                        role = SharedMemberRole.MEMBER,
                        isOnline = pid in onlineIds
                    )
                },
                lastMessagePreview = summary.lastMessagePreview,
                lastMessageAt = summary.lastMessageAt,
                unreadCount = summary.unreadCount,
                createdAt = "",
                disappearAfterSeconds = summary.disappearAfterSeconds,
                isPinned = summary.isPinned
            )
        }

        return ApiResponseBuilder.ok(
            PaginatedResponse(items = items, nextCursor = page.nextCursor, hasMore = page.hasMore)
        )
    }

    @DeleteMapping("/{conversationId}")
    fun deleteConversation(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        val conversation = conversationRepository.findById(conversationId)
            ?: throw com.muhabbet.shared.exception.BusinessException(com.muhabbet.shared.exception.ErrorCode.CONV_NOT_FOUND)

        if (conversation.type == ConversationType.GROUP) {
            manageGroupUseCase.leaveGroup(conversationId, userId)
        } else {
            // DM: just remove user from conversation_members to hide it
            conversationRepository.removeMember(conversationId, userId)
        }

        return ApiResponseBuilder.ok(Unit)
    }

    @PutMapping("/{conversationId}/pin")
    fun pinConversation(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        conversationRepository.pinConversation(conversationId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{conversationId}/pin")
    fun unpinConversation(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        conversationRepository.unpinConversation(conversationId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    // ─── Archive ──────────────────────────────────────────────

    @PutMapping("/{conversationId}/archive")
    fun archiveConversation(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        conversationRepository.archiveConversation(conversationId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{conversationId}/archive")
    fun unarchiveConversation(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        conversationRepository.unarchiveConversation(conversationId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    // ─── Mute ─────────────────────────────────────────────────

    @PutMapping("/{conversationId}/mute")
    fun muteConversation(
        @PathVariable conversationId: UUID,
        @RequestBody request: MuteRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        val mutedUntil = when (request.duration) {
            "8h" -> java.time.Instant.now().plusSeconds(8 * 3600)
            "1w" -> java.time.Instant.now().plusSeconds(7 * 24 * 3600)
            "always" -> java.time.Instant.parse("2099-12-31T23:59:59Z")
            else -> java.time.Instant.now().plusSeconds(8 * 3600)
        }
        conversationRepository.muteConversation(conversationId, userId, mutedUntil)
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{conversationId}/mute")
    fun unmuteConversation(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        conversationRepository.muteConversation(conversationId, userId, null)
        return ApiResponseBuilder.ok(Unit)
    }

    // ─── Lock ─────────────────────────────────────────────────

    @PutMapping("/{conversationId}/lock")
    fun lockConversation(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        conversationRepository.lockConversation(conversationId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{conversationId}/lock")
    fun unlockConversation(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        conversationRepository.unlockConversation(conversationId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    // ─── Announcement Mode ────────────────────────────────────

    @PutMapping("/{conversationId}/announcement")
    fun setAnnouncementMode(
        @PathVariable conversationId: UUID,
        @RequestBody request: AnnouncementRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        val conversation = conversationRepository.findById(conversationId)
            ?: throw com.muhabbet.shared.exception.BusinessException(com.muhabbet.shared.exception.ErrorCode.CONV_NOT_FOUND)

        // Only admin/owner can toggle announcement mode
        val member = conversationRepository.findMember(conversationId, userId)
            ?: throw com.muhabbet.shared.exception.BusinessException(com.muhabbet.shared.exception.ErrorCode.GROUP_NOT_MEMBER)
        if (member.role == com.muhabbet.messaging.domain.model.MemberRole.MEMBER) {
            throw com.muhabbet.shared.exception.BusinessException(com.muhabbet.shared.exception.ErrorCode.GROUP_PERMISSION_DENIED)
        }

        val updated = conversation.copy(announcementOnly = request.enabled, updatedAt = java.time.Instant.now())
        conversationRepository.updateConversation(updated)
        return ApiResponseBuilder.ok(Unit)
    }
}

data class MuteRequest(val duration: String)
data class AnnouncementRequest(val enabled: Boolean)

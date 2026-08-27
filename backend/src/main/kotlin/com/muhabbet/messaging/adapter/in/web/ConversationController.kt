package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.messaging.domain.port.`in`.GetConversationsUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.dto.AnnouncementModeResponse
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.SetAnnouncementModeRequest
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
    private val presencePort: PresencePort,
    private val blockPolicy: BlockPolicyPort
) {

    /**
     * The set of participants whose online dot must be withheld from [viewerId].
     *
     * This endpoint, not the profile screen, is where a blocked person actually watches you: the
     * mobile chat list seeds its dot straight from `ParticipantResponse.isOnline`. A guard on
     * `GET /users/{id}` alone would have left the live indicator on the screen users open first.
     *
     * **Both directions (#711)**, the same union `StatusService` applies to the Updates tab. This
     * asked only "who has blocked me", so the person who pressed Block went on watching their
     * blocked contact's dot light up in the chat list every day — the half they were actually
     * asking for, and the half that was missing. A block is not a request to be less visible; it is
     * a request to be done with someone, and presence is the channel that makes "done" visible.
     *
     * Two batched queries for the whole page, not two per participant — this is the app's busiest
     * call, and either direction resolved per row would be an N+1 on the screen users open first.
     */
    private fun presenceHiddenFrom(viewerId: UUID, participantIds: List<UUID>): Set<UUID> {
        val others = participantIds.filter { it != viewerId }
        return blockPolicy.findBlockedBy(viewerId, others) + blockPolicy.findBlockedAmong(viewerId, others)
    }

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
        val onlineIds = presencePort.getOnlineUserIds(memberUserIds) - presenceHiddenFrom(userId, memberUserIds)

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
        val onlineIds =
            presencePort.getOnlineUserIds(allParticipantIds) - presenceHiddenFrom(userId, allParticipantIds)

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
                // Backend domain enum → shared enum, the same hand-off `MessageMapper` makes. The
                // two are kept separate on purpose (the domain must not import the wire format), so
                // the name is the contract; `valueOf` on a member the shared enum lacks would throw
                // and take the whole list with it, hence the null fallback.
                lastMessageContentType = summary.lastMessageContentType?.let {
                    runCatching { com.muhabbet.shared.model.ContentType.valueOf(it.name) }.getOrNull()
                },
                lastMessageAt = summary.lastMessageAt,
                unreadCount = summary.unreadCount,
                createdAt = "",
                disappearAfterSeconds = summary.disappearAfterSeconds,
                isPinned = summary.isPinned,
                isMuted = summary.isMuted,
                isArchived = summary.isArchived,
                isLocked = summary.isLocked
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

    /**
     * The body is the shared [SetAnnouncementModeRequest], and the reply echoes what was stored.
     *
     * Both halves of that matter (#509). The group screen used to PATCH `{"announcementOnly": …}`
     * at the update-group route instead, where the field does not exist and `ignoreUnknownKeys`
     * discarded it behind a 200; sharing the DTO with the client is what stops that shape drifting
     * again. And returning the stored value lets the switch show server truth rather than flipping
     * on hope — for a control that decides who may speak, being wrong in either direction is bad.
     */
    @PutMapping("/{conversationId}/announcement")
    fun setAnnouncementMode(
        @PathVariable conversationId: UUID,
        @RequestBody request: SetAnnouncementModeRequest
    ): ResponseEntity<ApiResponse<AnnouncementModeResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val updated = manageGroupUseCase.setAnnouncementMode(conversationId, userId, request.enabled)
        return ApiResponseBuilder.ok(AnnouncementModeResponse(announcementOnly = updated.announcementOnly))
    }
}

data class MuteRequest(val duration: String)

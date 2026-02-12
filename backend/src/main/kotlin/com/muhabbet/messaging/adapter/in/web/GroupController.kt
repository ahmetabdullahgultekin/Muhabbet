package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.dto.AddMembersRequest
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.dto.UpdateGroupRequest
import com.muhabbet.shared.dto.UpdateRoleRequest
import com.muhabbet.shared.model.MemberRole as SharedMemberRole
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/conversations")
class GroupController(
    private val manageGroupUseCase: ManageGroupUseCase,
    private val userRepository: UserRepository,
    private val presencePort: PresencePort
) {

    @PostMapping("/{conversationId}/members")
    fun addMembers(
        @PathVariable conversationId: UUID,
        @RequestBody request: AddMembersRequest
    ): ResponseEntity<ApiResponse<List<ParticipantResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val userIds = request.userIds.map { UUID.fromString(it) }

        val addedMembers = manageGroupUseCase.addMembers(conversationId, userId, userIds)

        val onlineIds = presencePort.getOnlineUserIds(addedMembers.map { it.userId })
        val participants = addedMembers.map { m ->
            val user = userRepository.findById(m.userId)
            ParticipantResponse(
                userId = m.userId.toString(),
                displayName = user?.displayName,
                phoneNumber = user?.phoneNumber,
                avatarUrl = user?.avatarUrl,
                role = SharedMemberRole.valueOf(m.role.name),
                isOnline = m.userId in onlineIds
            )
        }

        return ApiResponseBuilder.ok(participants)
    }

    @DeleteMapping("/{conversationId}/members/{targetUserId}")
    fun removeMember(
        @PathVariable conversationId: UUID,
        @PathVariable targetUserId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageGroupUseCase.removeMember(conversationId, userId, targetUserId)
        return ApiResponseBuilder.ok(Unit)
    }

    @PatchMapping("/{conversationId}")
    fun updateGroupInfo(
        @PathVariable conversationId: UUID,
        @RequestBody request: UpdateGroupRequest
    ): ResponseEntity<ApiResponse<ConversationResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val updated = manageGroupUseCase.updateGroupInfo(conversationId, userId, request.name, request.description)

        val response = ConversationResponse(
            id = updated.id.toString(),
            type = com.muhabbet.shared.model.ConversationType.GROUP,
            name = updated.name,
            avatarUrl = updated.avatarUrl,
            participants = emptyList(),
            lastMessagePreview = null,
            lastMessageAt = null,
            unreadCount = 0,
            createdAt = updated.createdAt.toString()
        )

        return ApiResponseBuilder.ok(response)
    }

    @PatchMapping("/{conversationId}/members/{targetUserId}/role")
    fun updateMemberRole(
        @PathVariable conversationId: UUID,
        @PathVariable targetUserId: UUID,
        @RequestBody request: UpdateRoleRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        val role = MemberRole.valueOf(request.role.name)
        manageGroupUseCase.updateMemberRole(conversationId, userId, targetUserId, role)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/{conversationId}/leave")
    fun leaveGroup(
        @PathVariable conversationId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageGroupUseCase.leaveGroup(conversationId, userId)
        return ApiResponseBuilder.ok(Unit)
    }
}

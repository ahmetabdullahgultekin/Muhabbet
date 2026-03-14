package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageCommunityUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateCommunityRequest(val name: String, val description: String? = null)
data class AddGroupRequest(val conversationId: String)
data class AddMemberRequest(val userId: String)

data class CommunityResponse(
    val id: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val createdBy: String,
    val createdAt: String
)

data class CommunityDetailsResponse(
    val community: CommunityResponse,
    val groups: List<CommunityGroupResponse>,
    val members: List<CommunityMemberResponse>
)

data class CommunityGroupResponse(val communityId: String, val conversationId: String, val addedAt: String)
data class CommunityMemberResponse(val communityId: String, val userId: String, val role: String, val joinedAt: String)

@RestController
@RequestMapping("/api/v1/communities")
class CommunityController(
    private val manageCommunityUseCase: ManageCommunityUseCase
) {

    @PostMapping
    fun create(@RequestBody request: CreateCommunityRequest): ResponseEntity<ApiResponse<CommunityResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val community = manageCommunityUseCase.create(request.name, request.description, userId)
        return ApiResponseBuilder.created(community.toResponse())
    }

    @GetMapping
    fun listMyCommunities(): ResponseEntity<ApiResponse<List<CommunityResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val communities = manageCommunityUseCase.listForUser(userId)
        return ApiResponseBuilder.ok(communities.map { it.toResponse() })
    }

    @GetMapping("/{communityId}")
    fun getDetails(@PathVariable communityId: UUID): ResponseEntity<ApiResponse<CommunityDetailsResponse>> {
        val details = manageCommunityUseCase.getDetails(communityId)
        val response = CommunityDetailsResponse(
            community = details.community.toResponse(),
            groups = details.groups.map {
                CommunityGroupResponse(
                    communityId = it.communityId.toString(),
                    conversationId = it.conversationId.toString(),
                    addedAt = it.addedAt.toString()
                )
            },
            members = details.members.map {
                CommunityMemberResponse(
                    communityId = it.communityId.toString(),
                    userId = it.userId.toString(),
                    role = it.role.name,
                    joinedAt = it.joinedAt.toString()
                )
            }
        )
        return ApiResponseBuilder.ok(response)
    }

    @PostMapping("/{communityId}/groups")
    fun addGroup(
        @PathVariable communityId: UUID,
        @RequestBody request: AddGroupRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageCommunityUseCase.addGroup(communityId, UUID.fromString(request.conversationId), userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{communityId}/groups/{conversationId}")
    fun removeGroup(
        @PathVariable communityId: UUID,
        @PathVariable conversationId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageCommunityUseCase.removeGroup(communityId, conversationId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/{communityId}/members")
    fun addMember(
        @PathVariable communityId: UUID,
        @RequestBody request: AddMemberRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val requesterId = AuthenticatedUser.currentUserId()
        manageCommunityUseCase.addMember(communityId, UUID.fromString(request.userId), requesterId)
        return ApiResponseBuilder.ok(Unit)
    }
}

private fun com.muhabbet.messaging.domain.model.Community.toResponse() = CommunityResponse(
    id = id.toString(),
    name = name,
    description = description,
    avatarUrl = avatarUrl,
    createdBy = createdBy.toString(),
    createdAt = createdAt.toString()
)

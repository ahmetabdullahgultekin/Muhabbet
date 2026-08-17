package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.CommunityDetails
import com.muhabbet.messaging.domain.port.`in`.CommunitySummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateCommunityRequest(val name: String, val description: String? = null)
data class UpdateCommunityRequest(val name: String, val description: String? = null)
data class AddGroupRequest(val conversationId: String)

/**
 * Field-for-field the client's `CommunityResponse` in `shared/.../dto/Dtos.kt`. The shared DTOs
 * are the contract; anything added here that is not there is invisible to the app.
 */
data class CommunityResponse(
    val id: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val groupCount: Int,
    val memberCount: Int,
    val createdAt: String
)

/** Field-for-field the client's `CommunityDetailResponse`. */
data class CommunityDetailResponse(
    val id: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val groups: List<CommunityGroupInfoResponse>,
    val memberCount: Int,
    val myRole: String?,
    val createdAt: String,
    val announcementGroupId: String?
)

/** Field-for-field the client's `CommunityGroupInfo`. */
data class CommunityGroupInfoResponse(
    val conversationId: String,
    val name: String?,
    val avatarUrl: String?,
    val memberCount: Int
)

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
    fun getDetails(@PathVariable communityId: UUID): ResponseEntity<ApiResponse<CommunityDetailResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val details = manageCommunityUseCase.getDetails(communityId, userId)
        return ApiResponseBuilder.ok(details.toResponse())
    }

    @PatchMapping("/{communityId}")
    fun update(
        @PathVariable communityId: UUID,
        @RequestBody request: UpdateCommunityRequest
    ): ResponseEntity<ApiResponse<CommunityResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val updated = manageCommunityUseCase.update(communityId, userId, request.name, request.description)
        return ApiResponseBuilder.ok(updated.toResponse())
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

    @DeleteMapping("/{communityId}")
    fun delete(@PathVariable communityId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageCommunityUseCase.delete(communityId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

}

private fun CommunitySummary.toResponse() = CommunityResponse(
    id = community.id.toString(),
    name = community.name,
    description = community.description,
    avatarUrl = community.avatarUrl,
    groupCount = groupCount,
    memberCount = memberCount,
    createdAt = community.createdAt.toString()
)

private fun CommunityDetails.toResponse() = CommunityDetailResponse(
    id = community.id.toString(),
    name = community.name,
    description = community.description,
    avatarUrl = community.avatarUrl,
    groups = groups.map {
        CommunityGroupInfoResponse(
            conversationId = it.conversationId.toString(),
            name = it.name,
            avatarUrl = it.avatarUrl,
            memberCount = it.memberCount
        )
    },
    memberCount = memberCount,
    // Always present: only members can read a community. The field stays nullable because the
    // client's `CommunityDetailResponse.myRole` is nullable and the wire shape must not change.
    myRole = myRole.name,
    createdAt = community.createdAt.toString(),
    announcementGroupId = announcementGroupId?.toString()
)

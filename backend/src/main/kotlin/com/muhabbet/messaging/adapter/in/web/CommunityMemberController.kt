package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.CommunityMemberCandidate
import com.muhabbet.messaging.domain.port.`in`.CommunityMemberSummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityMembershipUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class AddMemberRequest(val userId: String)

/** Field-for-field the client's `CommunityMemberResponse` in `shared/.../dto/Dtos.kt`. */
data class CommunityMemberResponse(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val role: String,
    val joinedAt: String
)

/** Field-for-field the client's `CommunityMemberCandidateResponse`. */
data class CommunityMemberCandidateResponse(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?
)

/**
 * Who is in a community.
 *
 * Separate from [CommunityController] because it serves a different screen and depends on a
 * different in-port; the URL space is shared but the reasons to change are not.
 */
@RestController
@RequestMapping("/api/v1/communities")
class CommunityMemberController(
    private val membershipUseCase: ManageCommunityMembershipUseCase
) {

    /** Members only — see [ManageCommunityMembershipUseCase.listMembers]. */
    @GetMapping("/{communityId}/members")
    fun listMembers(
        @PathVariable communityId: UUID
    ): ResponseEntity<ApiResponse<List<CommunityMemberResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val members = membershipUseCase.listMembers(communityId, userId)
        return ApiResponseBuilder.ok(members.map { it.toResponse() })
    }

    /** Admins and owners only — it enumerates the membership of every linked group. */
    @GetMapping("/{communityId}/member-candidates")
    fun listMemberCandidates(
        @PathVariable communityId: UUID
    ): ResponseEntity<ApiResponse<List<CommunityMemberCandidateResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val candidates = membershipUseCase.listAddableUsers(communityId, userId)
        return ApiResponseBuilder.ok(candidates.map { it.toResponse() })
    }

    @PostMapping("/{communityId}/members")
    fun addMember(
        @PathVariable communityId: UUID,
        @RequestBody request: AddMemberRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val requesterId = AuthenticatedUser.currentUserId()
        membershipUseCase.addMember(communityId, UUID.fromString(request.userId), requesterId)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/{communityId}/leave")
    fun leave(@PathVariable communityId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        membershipUseCase.leave(communityId, userId)
        return ApiResponseBuilder.ok(Unit)
    }
}

private fun CommunityMemberSummary.toResponse() = CommunityMemberResponse(
    userId = userId.toString(),
    displayName = displayName,
    avatarUrl = avatarUrl,
    role = role.name,
    joinedAt = joinedAt.toString()
)

private fun CommunityMemberCandidate.toResponse() = CommunityMemberCandidateResponse(
    userId = userId.toString(),
    displayName = displayName,
    avatarUrl = avatarUrl
)

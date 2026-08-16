package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.BroadcastListMemberSummary
import com.muhabbet.messaging.domain.port.`in`.BroadcastListSummary
import com.muhabbet.messaging.domain.port.`in`.ManageBroadcastListUseCase
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

data class CreateBroadcastListRequest(val name: String, val memberIds: List<String> = emptyList())
data class AddBroadcastMembersRequest(val memberIds: List<String>)

/**
 * Field-for-field the client's `BroadcastListResponse` in `shared/.../dto/Dtos.kt`. The shared DTOs
 * are the contract; a field declared there and not written here is not absent, it is silently the
 * client's default — which is how every list rendered "0 üye" (#392).
 */
data class BroadcastListResponse(
    val id: String,
    val name: String,
    val memberCount: Int,
    val createdAt: String
)

/** Field-for-field the client's `BroadcastMemberResponse`. */
data class BroadcastMemberResponse(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?
)

@RestController
@RequestMapping("/api/v1/broadcast-lists")
class BroadcastListController(
    private val manageBroadcastListUseCase: ManageBroadcastListUseCase
) {

    @PostMapping
    fun create(@RequestBody request: CreateBroadcastListRequest): ResponseEntity<ApiResponse<BroadcastListResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val memberIds = request.memberIds.map { UUID.fromString(it) }
        val created = manageBroadcastListUseCase.create(userId, request.name, memberIds)
        return ApiResponseBuilder.created(created.toResponse())
    }

    @GetMapping
    fun getMyLists(): ResponseEntity<ApiResponse<List<BroadcastListResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val lists = manageBroadcastListUseCase.getByOwner(userId)
        return ApiResponseBuilder.ok(lists.map { it.toResponse() })
    }

    @GetMapping("/{listId}/members")
    fun getMembers(@PathVariable listId: UUID): ResponseEntity<ApiResponse<List<BroadcastMemberResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val members = manageBroadcastListUseCase.getMembers(listId, userId)
        return ApiResponseBuilder.ok(members.map { it.toResponse() })
    }

    @PostMapping("/{listId}/members")
    fun addMembers(
        @PathVariable listId: UUID,
        @RequestBody request: AddBroadcastMembersRequest
    ): ResponseEntity<ApiResponse<List<BroadcastMemberResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val memberIds = request.memberIds.map { UUID.fromString(it) }
        val added = manageBroadcastListUseCase.addMembers(listId, userId, memberIds)
        return ApiResponseBuilder.ok(added.map { it.toResponse() })
    }

    @DeleteMapping("/{listId}/members/{memberId}")
    fun removeMember(
        @PathVariable listId: UUID,
        @PathVariable memberId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageBroadcastListUseCase.removeMember(listId, userId, memberId)
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{listId}")
    fun delete(@PathVariable listId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageBroadcastListUseCase.delete(listId, userId)
        return ApiResponseBuilder.ok(Unit)
    }
}

private fun BroadcastListSummary.toResponse() = BroadcastListResponse(
    id = list.id.toString(),
    name = list.name,
    memberCount = memberCount,
    createdAt = list.createdAt.toString()
)

private fun BroadcastListMemberSummary.toResponse() = BroadcastMemberResponse(
    userId = userId.toString(),
    displayName = displayName,
    avatarUrl = avatarUrl
)

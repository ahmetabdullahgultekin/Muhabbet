package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.BroadcastList
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
data class BroadcastListResponse(val id: String, val name: String, val createdAt: String)
data class BroadcastMemberResponse(val broadcastListId: String, val userId: String)

@RestController
@RequestMapping("/api/v1/broadcast-lists")
class BroadcastListController(
    private val manageBroadcastListUseCase: ManageBroadcastListUseCase
) {

    @PostMapping
    fun create(@RequestBody request: CreateBroadcastListRequest): ResponseEntity<ApiResponse<BroadcastListResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val memberIds = request.memberIds.map { UUID.fromString(it) }
        val list = manageBroadcastListUseCase.create(userId, request.name, memberIds)
        return ApiResponseBuilder.created(list.toResponse())
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
        return ApiResponseBuilder.ok(members.map {
            BroadcastMemberResponse(broadcastListId = it.broadcastListId.toString(), userId = it.userId.toString())
        })
    }

    @PostMapping("/{listId}/members")
    fun addMembers(
        @PathVariable listId: UUID,
        @RequestBody request: AddBroadcastMembersRequest
    ): ResponseEntity<ApiResponse<List<BroadcastMemberResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val memberIds = request.memberIds.map { UUID.fromString(it) }
        val added = manageBroadcastListUseCase.addMembers(listId, userId, memberIds)
        return ApiResponseBuilder.ok(added.map {
            BroadcastMemberResponse(broadcastListId = it.broadcastListId.toString(), userId = it.userId.toString())
        })
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

private fun BroadcastList.toResponse() = BroadcastListResponse(
    id = id.toString(),
    name = name,
    createdAt = createdAt.toString()
)

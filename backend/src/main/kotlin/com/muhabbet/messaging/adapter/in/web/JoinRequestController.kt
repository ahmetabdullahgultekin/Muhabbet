package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.port.`in`.ManageJoinRequestUseCase
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

data class JoinRequestBody(val linkId: String? = null)

data class JoinRequestResponse(
    val id: String,
    val conversationId: String,
    val userId: String,
    val status: String,
    val createdAt: String,
    val reviewedAt: String?
)

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/join-requests")
class JoinRequestController(
    private val manageJoinRequestUseCase: ManageJoinRequestUseCase
) {

    @PostMapping
    fun requestJoin(
        @PathVariable conversationId: UUID,
        @RequestBody(required = false) body: JoinRequestBody?
    ): ResponseEntity<ApiResponse<JoinRequestResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val linkId = body?.linkId?.let { UUID.fromString(it) }
        val request = manageJoinRequestUseCase.requestJoin(conversationId, userId, linkId)
        return ApiResponseBuilder.created(request.toResponse())
    }

    @GetMapping
    fun getPendingRequests(
        @PathVariable conversationId: UUID
    ): ResponseEntity<ApiResponse<List<JoinRequestResponse>>> {
        val requests = manageJoinRequestUseCase.getPendingRequests(conversationId)
        return ApiResponseBuilder.ok(requests.map { it.toResponse() })
    }

    @PostMapping("/{requestId}/approve")
    fun approveJoin(
        @PathVariable conversationId: UUID,
        @PathVariable requestId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageJoinRequestUseCase.approveJoin(requestId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/{requestId}/reject")
    fun rejectJoin(
        @PathVariable conversationId: UUID,
        @PathVariable requestId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageJoinRequestUseCase.rejectJoin(requestId, userId)
        return ApiResponseBuilder.ok(Unit)
    }
}

private fun GroupJoinRequest.toResponse() = JoinRequestResponse(
    id = id.toString(),
    conversationId = conversationId.toString(),
    userId = userId.toString(),
    status = status.name,
    createdAt = createdAt.toString(),
    reviewedAt = reviewedAt?.toString()
)

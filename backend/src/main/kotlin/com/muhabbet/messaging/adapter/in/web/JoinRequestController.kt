package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.port.`in`.ManageJoinRequestUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.JoinRequestResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Review side of join requests only. There is deliberately **no** `POST` here (#788 review): the
 * only way to file a request is `InviteLinkService.joinViaLink`, which first proves the caller holds
 * an active, unexpired, not-exhausted link *of this group*. The endpoint that used to sit here took
 * any group id and a caller-chosen `linkId` that nothing validated, so anyone who knew a group's id
 * could queue requests — name and avatar included — in front of its admins, and an approval would
 * bump the use count of whatever link id they had typed, another group's included. No client ever
 * called it.
 */
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/join-requests")
class JoinRequestController(
    private val manageJoinRequestUseCase: ManageJoinRequestUseCase
) {

    @GetMapping
    fun getPendingRequests(
        @PathVariable conversationId: UUID
    ): ResponseEntity<ApiResponse<List<JoinRequestResponse>>> {
        val requesterId = AuthenticatedUser.currentUserId()
        val requests = manageJoinRequestUseCase.getPendingRequests(conversationId, requesterId)
        return ApiResponseBuilder.ok(
            requests.map { it.request.toResponse(it.displayName, it.avatarUrl) }
        )
    }

    @PostMapping("/{requestId}/approve")
    fun approveJoin(
        @PathVariable conversationId: UUID,
        @PathVariable requestId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageJoinRequestUseCase.approveJoin(conversationId, requestId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/{requestId}/reject")
    fun rejectJoin(
        @PathVariable conversationId: UUID,
        @PathVariable requestId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageJoinRequestUseCase.rejectJoin(conversationId, requestId, userId)
        return ApiResponseBuilder.ok(Unit)
    }
}

/**
 * The shared [JoinRequestResponse] (#788) — the backend used to answer with a private copy whose
 * shape no client had ever read. The requester's name and avatar are filled only on the admin's
 * pending list; never their phone number.
 */
private fun GroupJoinRequest.toResponse(displayName: String? = null, avatarUrl: String? = null) = JoinRequestResponse(
    id = id.toString(),
    userId = userId.toString(),
    displayName = displayName,
    avatarUrl = avatarUrl,
    status = status.name,
    createdAt = createdAt.toString()
)

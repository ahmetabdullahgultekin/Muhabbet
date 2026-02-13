package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.Status
import com.muhabbet.messaging.domain.port.`in`.ManageStatusUseCase
import com.muhabbet.messaging.domain.port.`in`.StatusGroup
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.StatusCreateRequest
import com.muhabbet.shared.dto.StatusResponse
import com.muhabbet.shared.dto.UserStatusGroup
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

@RestController
@RequestMapping("/api/v1/statuses")
class StatusController(
    private val manageStatusUseCase: ManageStatusUseCase
) {

    @PostMapping
    fun createStatus(@RequestBody request: StatusCreateRequest): ResponseEntity<ApiResponse<StatusResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val status = manageStatusUseCase.createStatus(userId, request.content, request.mediaUrl)
        return ApiResponseBuilder.ok(status.toResponse())
    }

    @GetMapping("/me")
    fun getMyStatuses(): ResponseEntity<ApiResponse<List<StatusResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val statuses = manageStatusUseCase.getMyStatuses(userId)
        return ApiResponseBuilder.ok(statuses.map { it.toResponse() })
    }

    @GetMapping("/contacts")
    fun getContactStatuses(): ResponseEntity<ApiResponse<List<UserStatusGroup>>> {
        val groups = manageStatusUseCase.getContactStatuses()
        return ApiResponseBuilder.ok(groups.map { it.toDto() })
    }

    @DeleteMapping("/{statusId}")
    fun deleteStatus(@PathVariable statusId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageStatusUseCase.deleteStatus(statusId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    private fun Status.toResponse() = StatusResponse(
        id = id.toString(),
        userId = userId.toString(),
        content = content,
        mediaUrl = mediaUrl,
        createdAt = createdAt.toEpochMilli(),
        expiresAt = expiresAt.toEpochMilli()
    )

    private fun StatusGroup.toDto() = UserStatusGroup(
        userId = userId.toString(),
        statuses = statuses.map { it.toResponse() }
    )
}

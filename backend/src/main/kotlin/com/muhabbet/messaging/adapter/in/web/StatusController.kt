package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.adapter.out.persistence.entity.StatusJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataStatusRepository
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
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/statuses")
class StatusController(
    private val statusRepo: SpringDataStatusRepository
) {

    @PostMapping
    fun createStatus(@RequestBody request: StatusCreateRequest): ResponseEntity<ApiResponse<StatusResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val entity = StatusJpaEntity(
            userId = userId,
            content = request.content,
            mediaUrl = request.mediaUrl
        )
        val saved = statusRepo.save(entity)
        return ApiResponseBuilder.ok(saved.toResponse())
    }

    @GetMapping("/me")
    fun getMyStatuses(): ResponseEntity<ApiResponse<List<StatusResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val statuses = statusRepo.findActiveByUserId(userId, Instant.now())
        return ApiResponseBuilder.ok(statuses.map { it.toResponse() })
    }

    @GetMapping("/contacts")
    fun getContactStatuses(): ResponseEntity<ApiResponse<List<UserStatusGroup>>> {
        // For MVP, return all active statuses grouped by user
        val now = Instant.now()
        val all = statusRepo.findAll()
            .filter { it.expiresAt.isAfter(now) }
            .groupBy { it.userId }
            .map { (userId, statuses) ->
                UserStatusGroup(
                    userId = userId.toString(),
                    statuses = statuses.sortedByDescending { it.createdAt }.map { it.toResponse() }
                )
            }
        return ApiResponseBuilder.ok(all)
    }

    @DeleteMapping("/{statusId}")
    fun deleteStatus(@PathVariable statusId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        val status = statusRepo.findById(statusId).orElse(null)
        if (status != null && status.userId == userId) {
            statusRepo.delete(status)
        }
        return ApiResponseBuilder.ok(Unit)
    }

    private fun StatusJpaEntity.toResponse() = StatusResponse(
        id = id.toString(),
        userId = userId.toString(),
        content = content,
        mediaUrl = mediaUrl,
        createdAt = createdAt.toEpochMilli(),
        expiresAt = expiresAt.toEpochMilli()
    )
}

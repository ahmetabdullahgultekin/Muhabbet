package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManagePinnedMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.toInfo
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.PinMessageRequest
import com.muhabbet.shared.dto.PinnedMessageInfo
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
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

/**
 * The request and response types live in the shared module (#790) so the mobile client decodes the
 * classes the server encodes; until then they were private to this file and no client existed.
 */
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/pinned-messages")
class PinnedMessageController(
    private val managePinnedMessageUseCase: ManagePinnedMessageUseCase
) {

    @PostMapping
    fun pin(
        @PathVariable conversationId: UUID,
        @RequestBody request: PinMessageRequest
    ): ResponseEntity<ApiResponse<PinnedMessageInfo>> {
        val userId = AuthenticatedUser.currentUserId()
        val messageId = try {
            UUID.fromString(request.messageId)
        } catch (_: IllegalArgumentException) {
            // A malformed id names no message; answering 404 says so instead of a 500.
            throw BusinessException(ErrorCode.MSG_NOT_FOUND)
        }
        val pin = managePinnedMessageUseCase.pin(conversationId, messageId, userId)
        return ApiResponseBuilder.created(pin.toInfo())
    }

    @GetMapping
    fun getPinned(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<List<PinnedMessageInfo>>> {
        val userId = AuthenticatedUser.currentUserId()
        return ApiResponseBuilder.ok(managePinnedMessageUseCase.getPinned(conversationId, userId).map { it.toInfo() })
    }

    @DeleteMapping("/{messageId}")
    fun unpin(
        @PathVariable conversationId: UUID,
        @PathVariable messageId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        managePinnedMessageUseCase.unpin(conversationId, messageId, userId)
        return ApiResponseBuilder.ok(Unit)
    }
}

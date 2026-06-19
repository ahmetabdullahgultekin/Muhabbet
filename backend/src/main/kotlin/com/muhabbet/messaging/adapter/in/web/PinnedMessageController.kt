package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.PinnedMessage
import com.muhabbet.messaging.domain.port.`in`.ManagePinnedMessageUseCase
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

data class PinMessageRequest(val messageId: String)
data class PinnedMessageResponse(
    val conversationId: String,
    val messageId: String,
    val pinnedBy: String,
    val pinnedAt: String
)

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/pinned-messages")
class PinnedMessageController(
    private val managePinnedMessageUseCase: ManagePinnedMessageUseCase
) {

    @PostMapping
    fun pin(
        @PathVariable conversationId: UUID,
        @RequestBody request: PinMessageRequest
    ): ResponseEntity<ApiResponse<PinnedMessageResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val pin = managePinnedMessageUseCase.pin(conversationId, UUID.fromString(request.messageId), userId)
        return ApiResponseBuilder.created(pin.toResponse())
    }

    @GetMapping
    fun getPinned(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<List<PinnedMessageResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        return ApiResponseBuilder.ok(managePinnedMessageUseCase.getPinned(conversationId, userId).map { it.toResponse() })
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

private fun PinnedMessage.toResponse() = PinnedMessageResponse(
    conversationId = conversationId.toString(),
    messageId = messageId.toString(),
    pinnedBy = pinnedBy.toString(),
    pinnedAt = pinnedAt.toString()
)

package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.GetStarredMessagesUseCase
import com.muhabbet.messaging.domain.port.`in`.StarMessageUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.model.Message as SharedMessage
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Depends on use cases only (#906). It used to hold the starred out-port and call it directly,
 * which is how the feature shipped with no membership check at all.
 */
@RestController
@RequestMapping("/api/v1/starred")
class StarredMessageController(
    private val starMessageUseCase: StarMessageUseCase,
    private val getStarredMessagesUseCase: GetStarredMessagesUseCase,
    private val getMessageHistoryUseCase: GetMessageHistoryUseCase
) {

    @PostMapping("/{messageId}")
    fun starMessage(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<Unit>> {
        starMessageUseCase.star(AuthenticatedUser.currentUserId(), messageId)
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{messageId}")
    fun unstarMessage(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<Unit>> {
        starMessageUseCase.unstar(AuthenticatedUser.currentUserId(), messageId)
        return ApiResponseBuilder.ok(Unit)
    }

    @GetMapping
    fun getStarredMessages(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?
    ): ResponseEntity<ApiResponse<PaginatedResponse<SharedMessage>>> {
        val userId = AuthenticatedUser.currentUserId()
        val page = getStarredMessagesUseCase.getStarred(userId, cursor, limit)
        val messages = page.items.map { it.message }

        val statusMap = getMessageHistoryUseCase.resolveDeliveryStatuses(messages, userId)
        val items = messages.map { msg -> msg.toSharedMessage(statusMap[msg.id].toMessageStatus()) }

        return ApiResponseBuilder.ok(
            PaginatedResponse(items = items, nextCursor = page.nextCursor?.encode(), hasMore = page.hasMore)
        )
    }
}

package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageRepository
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.model.Message as SharedMessage
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val messageRepo: SpringDataMessageRepository,
    private val getMessageHistoryUseCase: GetMessageHistoryUseCase
) {

    @GetMapping("/messages")
    fun searchMessages(
        @RequestParam q: String,
        @RequestParam(required = false) conversationId: UUID?,
        @RequestParam(defaultValue = "30") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<ApiResponse<PaginatedResponse<SharedMessage>>> {
        val userId = AuthenticatedUser.currentUserId()

        val pageable = PageRequest.of(offset / limit, limit)
        val results = if (conversationId != null) {
            messageRepo.searchInConversation(conversationId, "%${q.lowercase()}%", pageable)
        } else {
            messageRepo.searchGlobal("%${q.lowercase()}%", pageable)
        }

        val domainMessages = results.map { it.toDomain() }
        val statusMap = getMessageHistoryUseCase.resolveDeliveryStatuses(domainMessages, userId)
        val items = domainMessages.map { msg ->
            msg.toSharedMessage(statusMap[msg.id].toMessageStatus())
        }

        return ApiResponseBuilder.ok(
            PaginatedResponse(items = items, nextCursor = null, hasMore = results.size >= limit)
        )
    }
}

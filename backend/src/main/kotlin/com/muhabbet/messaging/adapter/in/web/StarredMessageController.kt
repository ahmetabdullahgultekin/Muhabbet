package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.model.ContentType as SharedContentType
import com.muhabbet.shared.model.Message as SharedMessage
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import kotlinx.datetime.Instant as KInstant
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/starred")
class StarredMessageController(
    private val starredMessageRepository: StarredMessageRepository
) {

    @PostMapping("/{messageId}")
    fun starMessage(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        starredMessageRepository.star(userId, messageId)
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{messageId}")
    fun unstarMessage(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        starredMessageRepository.unstar(userId, messageId)
        return ApiResponseBuilder.ok(Unit)
    }

    @GetMapping
    fun getStarredMessages(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<ApiResponse<PaginatedResponse<SharedMessage>>> {
        val userId = AuthenticatedUser.currentUserId()
        val messages = starredMessageRepository.getStarredMessages(userId, limit, offset)

        val items = messages.map { msg ->
            SharedMessage(
                id = msg.id.toString(),
                conversationId = msg.conversationId.toString(),
                senderId = msg.senderId.toString(),
                contentType = SharedContentType.valueOf(msg.contentType.name),
                content = if (msg.isDeleted) "" else msg.content,
                replyToId = msg.replyToId?.toString(),
                mediaUrl = msg.mediaUrl,
                thumbnailUrl = msg.thumbnailUrl,
                status = MessageStatus.SENT,
                serverTimestamp = KInstant.fromEpochMilliseconds(msg.serverTimestamp.toEpochMilli()),
                clientTimestamp = KInstant.fromEpochMilliseconds(msg.clientTimestamp.toEpochMilli()),
                editedAt = msg.editedAt?.let { KInstant.fromEpochMilliseconds(it.toEpochMilli()) },
                isDeleted = msg.isDeleted
            )
        }

        return ApiResponseBuilder.ok(
            PaginatedResponse(items = items, nextCursor = null, hasMore = messages.size >= limit)
        )
    }
}

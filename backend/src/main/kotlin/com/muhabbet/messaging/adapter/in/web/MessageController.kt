package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.EditMessageRequest
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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class MessageController(
    private val getMessageHistoryUseCase: GetMessageHistoryUseCase,
    private val manageMessageUseCase: ManageMessageUseCase
) {

    @GetMapping("/conversations/{conversationId}/messages")
    fun getMessages(
        @PathVariable conversationId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "before") direction: String
    ): ResponseEntity<ApiResponse<PaginatedResponse<SharedMessage>>> {
        val userId = AuthenticatedUser.currentUserId()

        val page = getMessageHistoryUseCase.getMessages(
            conversationId = conversationId,
            userId = userId,
            cursor = cursor,
            limit = limit,
            direction = direction
        )

        val items = page.items.map { msg ->
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
            PaginatedResponse(items = items, nextCursor = page.nextCursor, hasMore = page.hasMore)
        )
    }

    @DeleteMapping("/messages/{messageId}")
    fun deleteMessage(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageMessageUseCase.deleteMessage(messageId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @PatchMapping("/messages/{messageId}")
    fun editMessage(
        @PathVariable messageId: UUID,
        @RequestBody request: EditMessageRequest
    ): ResponseEntity<ApiResponse<SharedMessage>> {
        val userId = AuthenticatedUser.currentUserId()
        val msg = manageMessageUseCase.editMessage(messageId, userId, request.content)

        val response = SharedMessage(
            id = msg.id.toString(),
            conversationId = msg.conversationId.toString(),
            senderId = msg.senderId.toString(),
            contentType = SharedContentType.valueOf(msg.contentType.name),
            content = msg.content,
            replyToId = msg.replyToId?.toString(),
            mediaUrl = msg.mediaUrl,
            thumbnailUrl = msg.thumbnailUrl,
            status = MessageStatus.SENT,
            serverTimestamp = KInstant.fromEpochMilliseconds(msg.serverTimestamp.toEpochMilli()),
            clientTimestamp = KInstant.fromEpochMilliseconds(msg.clientTimestamp.toEpochMilli()),
            editedAt = msg.editedAt?.let { KInstant.fromEpochMilliseconds(it.toEpochMilli()) },
            isDeleted = msg.isDeleted
        )

        return ApiResponseBuilder.ok(response)
    }
}

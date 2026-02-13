package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.EditMessageRequest
import com.muhabbet.shared.dto.MessageInfoResponse
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.dto.RecipientDeliveryInfo
import com.muhabbet.shared.model.Message as SharedMessage
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
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
    private val manageMessageUseCase: ManageMessageUseCase,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
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

        val statusMap = getMessageHistoryUseCase.resolveDeliveryStatuses(page.items, userId)
        val items = page.items.map { msg ->
            msg.toSharedMessage(statusMap[msg.id].toMessageStatus())
        }

        return ApiResponseBuilder.ok(
            PaginatedResponse(items = items, nextCursor = page.nextCursor, hasMore = page.hasMore)
        )
    }

    @GetMapping("/conversations/{conversationId}/media")
    fun getMedia(
        @PathVariable conversationId: UUID,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<ApiResponse<PaginatedResponse<SharedMessage>>> {
        val userId = AuthenticatedUser.currentUserId()
        val messages = getMessageHistoryUseCase.getMediaMessages(conversationId, userId, limit, offset)
        val statusMap = getMessageHistoryUseCase.resolveDeliveryStatuses(messages, userId)
        val items = messages.map { msg ->
            msg.toSharedMessage(statusMap[msg.id].toMessageStatus())
        }
        return ApiResponseBuilder.ok(
            PaginatedResponse(items = items, nextCursor = null, hasMore = messages.size >= limit)
        )
    }

    @GetMapping("/messages/{messageId}/info")
    fun getMessageInfo(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<MessageInfoResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)
        val statuses = messageRepository.getDeliveryStatuses(listOf(messageId))
        val recipientInfos = statuses
            .filter { it.userId != message.senderId }
            .map { ds ->
                val user = try { userRepository.findById(ds.userId) } catch (_: Exception) { null }
                RecipientDeliveryInfo(
                    userId = ds.userId.toString(),
                    displayName = user?.displayName ?: ds.userId.toString().take(8),
                    status = ds.status.name,
                    updatedAt = ds.updatedAt.toString()
                )
            }
        val info = MessageInfoResponse(
            messageId = message.id.toString(),
            conversationId = message.conversationId.toString(),
            senderId = message.senderId.toString(),
            content = if (message.isDeleted) "" else message.content,
            contentType = message.contentType.name,
            sentAt = message.serverTimestamp.toString(),
            recipients = recipientInfos
        )
        return ApiResponseBuilder.ok(info)
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
        return ApiResponseBuilder.ok(msg.toSharedMessage())
    }
}

internal fun DeliveryStatus?.toMessageStatus(): MessageStatus = when (this) {
    DeliveryStatus.SENT -> MessageStatus.SENT
    DeliveryStatus.DELIVERED -> MessageStatus.DELIVERED
    DeliveryStatus.READ -> MessageStatus.READ
    null -> MessageStatus.SENT
}

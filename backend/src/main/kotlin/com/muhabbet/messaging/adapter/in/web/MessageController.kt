package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.EditMessageRequest
import com.muhabbet.shared.dto.MessageInfoResponse
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.dto.RecipientDeliveryInfo
import com.muhabbet.shared.dto.SendMessageRequest
import com.muhabbet.shared.dto.ViewOnceRevealResponse
import com.muhabbet.shared.model.Message as SharedMessage
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class MessageController(
    private val getMessageHistoryUseCase: GetMessageHistoryUseCase,
    private val manageMessageUseCase: ManageMessageUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val updateDeliveryStatusUseCase: UpdateDeliveryStatusUseCase
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

    @GetMapping("/messages/since")
    fun getMessagesSince(
        @RequestParam timestamp: String
    ): ResponseEntity<ApiResponse<PaginatedResponse<SharedMessage>>> {
        val userId = AuthenticatedUser.currentUserId()
        val since = try {
            java.time.Instant.parse(timestamp)
        } catch (_: Exception) {
            throw BusinessException(ErrorCode.MSG_INVALID_CURSOR)
        }

        val messages = getMessageHistoryUseCase.getMessagesSince(userId, since)
        val statusMap = getMessageHistoryUseCase.resolveDeliveryStatuses(messages, userId)
        val items = messages.map { msg ->
            msg.toSharedMessage(statusMap[msg.id].toMessageStatus())
        }

        return ApiResponseBuilder.ok(
            PaginatedResponse(items = items, nextCursor = null, hasMore = false)
        )
    }

    /**
     * Sends a text message without a WebSocket.
     *
     * The same [SendMessageUseCase] the socket handler calls, so membership, announcement mode,
     * the duplicate-id check, the per-recipient delivery rows and the fan-out to online devices
     * are all the one implementation — this is a second transport, not a second send path.
     *
     * It exists because the Android notification inline reply runs in a `BroadcastReceiver` with
     * roughly ten seconds to live and, when the app has been swiped away, no socket to reach for
     * (#510). Nothing else should prefer it: a client holding a socket gets its ack back over the
     * socket and does not have to pay for a fresh TLS handshake per message.
     */
    @PostMapping("/conversations/{conversationId}/messages")
    fun sendMessage(
        @PathVariable conversationId: UUID,
        @RequestBody request: SendMessageRequest
    ): ResponseEntity<ApiResponse<SharedMessage>> {
        val userId = AuthenticatedUser.currentUserId()
        val message = sendMessageUseCase.sendMessage(
            SendMessageCommand(
                messageId = UUID.fromString(request.messageId),
                conversationId = conversationId,
                senderId = userId,
                content = request.content,
                contentType = ContentType.TEXT,
                clientTimestamp = Instant.now()
            )
        )
        return ApiResponseBuilder.ok(message.toSharedMessage())
    }

    /**
     * Records that this message reached the device, without a WebSocket.
     *
     * Exists for `MuhabbetFirebaseMessagingService.onMessageReceived` (#596): FCM hands a background
     * service the push and a few seconds to run, no open socket, and opening one just to send a
     * single [WsMessage.AckMessage] is exactly what battery optimisation kills first. Same use case
     * [ChatWebSocketHandler] calls for the WebSocket `DELIVERED` ack — this is a second transport
     * for an existing decision, same shape as [sendMessage] above, not a new one.
     *
     * Silently a no-op for a `messageId` this caller has no delivery row for (already read, blocked,
     * someone else's message) — [UpdateDeliveryStatusUseCase.updateStatus] already behaves this way
     * for the WebSocket ack, so a client cannot use this to probe whether an arbitrary id exists.
     */
    @PostMapping("/messages/{messageId}/delivered")
    fun markDelivered(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        updateDeliveryStatusUseCase.updateStatus(messageId, userId, DeliveryStatus.DELIVERED)
        return ApiResponseBuilder.ok(Unit)
    }

    @GetMapping("/messages/{messageId}/info")
    fun getMessageInfo(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<MessageInfoResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        // Lookup, membership authorization and recipient resolution all happen behind the use case
        // (closes IDOR). All that is left here is shaping the response.
        val (message, recipients) = getMessageHistoryUseCase.getMessageInfo(messageId, userId)
        val recipientInfos = recipients.map { recipient ->
            RecipientDeliveryInfo(
                userId = recipient.userId.toString(),
                displayName = recipient.displayName ?: recipient.userId.toString().take(8),
                avatarUrl = recipient.avatarUrl,
                status = recipient.status.name,
                // Null when the status was downgraded for a recipient with read receipts off; the
                // client already renders the row without a time in that case.
                updatedAt = recipient.updatedAt?.toString()
            )
        }
        val info = MessageInfoResponse(
            messageId = message.id.toString(),
            conversationId = message.conversationId.toString(),
            senderId = message.senderId.toString(),
            content = if (message.isDeleted) "" else message.content,
            contentType = message.contentType.name,
            sentAt = message.serverTimestamp.toString(),
            // Message info builds its own response rather than going through toSharedMessage, so the
            // view-once rule has to be repeated here. It is not decoration: "Info" is reachable from
            // the context menu on any message, and it was returning the full-resolution URL of a
            // sealed photo to every member of the conversation.
            mediaUrl = if (message.viewOnce) null else message.mediaUrl,
            thumbnailUrl = if (message.viewOnce) null else message.thumbnailUrl,
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

    /**
     * Opens a view-once message: burns it and returns its media, once.
     *
     * The only endpoint in the API that hands out a view-once blob URL. Every other response nulls
     * it (see [toSharedMessage]), which is what makes the seal a seal rather than a drawing of one —
     * before #515 the sealed bubble and a fully working URL for the photo behind it travelled in the
     * same payload.
     */
    @PostMapping("/messages/{messageId}/view-once")
    fun markViewOnceViewed(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<ViewOnceRevealResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val reveal = manageMessageUseCase.markViewOnceViewed(messageId, userId)
        return ApiResponseBuilder.ok(
            ViewOnceRevealResponse(
                messageId = reveal.messageId.toString(),
                mediaUrl = reveal.mediaUrl,
                thumbnailUrl = reveal.thumbnailUrl,
                viewedAt = reveal.viewedAt.toEpochMilli()
            )
        )
    }
}

internal fun DeliveryStatus?.toMessageStatus(): MessageStatus = when (this) {
    DeliveryStatus.SENT -> MessageStatus.SENT
    DeliveryStatus.DELIVERED -> MessageStatus.DELIVERED
    DeliveryStatus.READ -> MessageStatus.READ
    null -> MessageStatus.SENT
}

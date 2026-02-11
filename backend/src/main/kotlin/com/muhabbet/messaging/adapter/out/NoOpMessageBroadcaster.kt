package com.muhabbet.messaging.adapter.out

import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WebSocketMessageBroadcaster(
    private val sessionManager: WebSocketSessionManager
) : MessageBroadcaster {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun broadcastMessage(message: Message, recipientIds: List<UUID>) {
        val wsMessage = WsMessage.NewMessage(
            messageId = message.id.toString(),
            conversationId = message.conversationId.toString(),
            senderId = message.senderId.toString(),
            senderName = null,
            content = message.content,
            contentType = com.muhabbet.shared.model.ContentType.valueOf(message.contentType.name),
            replyToId = message.replyToId?.toString(),
            mediaUrl = message.mediaUrl,
            thumbnailUrl = message.thumbnailUrl,
            serverTimestamp = message.serverTimestamp.toEpochMilli()
        )

        val json = wsJson.encodeToString<WsMessage>(wsMessage)

        recipientIds.forEach { recipientId ->
            if (sessionManager.isOnline(recipientId)) {
                sessionManager.sendToUser(recipientId, json)
                log.debug("Message {} delivered to online user {}", message.id, recipientId)
            } else {
                log.debug("User {} offline, message {} queued in DB", recipientId, message.id)
            }
        }
    }

    override fun broadcastStatusUpdate(messageId: UUID, conversationId: UUID, userId: UUID, status: DeliveryStatus) {
        val wsStatus = when (status) {
            DeliveryStatus.DELIVERED -> MessageStatus.DELIVERED
            DeliveryStatus.READ -> MessageStatus.READ
            DeliveryStatus.SENT -> MessageStatus.SENT
        }

        val wsMessage = WsMessage.StatusUpdate(
            messageId = messageId.toString(),
            conversationId = conversationId.toString(),
            userId = userId.toString(),
            status = wsStatus,
            timestamp = System.currentTimeMillis()
        )

        val json = wsJson.encodeToString<WsMessage>(wsMessage)

        // Send to the original sender of the message so they see delivery/read status
        // The userId here is the one who delivered/read, we need to notify the sender
        // This is handled by the caller (MessagingService already knows the sender from message lookup)
        sessionManager.sendToUser(userId, json)
    }
}

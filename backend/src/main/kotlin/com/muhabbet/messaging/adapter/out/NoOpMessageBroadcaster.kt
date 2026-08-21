package com.muhabbet.messaging.adapter.out

import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.adapter.out.external.OfflinePushSender
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WebSocketMessageBroadcaster(
    private val sessionManager: WebSocketSessionManager,
    private val userDirectory: UserDirectoryPort,
    private val conversationRepository: ConversationRepository,
    private val offlinePushSender: OfflinePushSender
) : MessageBroadcaster {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun broadcastMessage(message: Message, recipients: List<ConversationMember>) {
        // Resolved once for the whole recipient list. This used to run per offline recipient inside
        // sendPushToOfflineUser, so a group re-read the same sender row for every member.
        val senderDisplayName = userDirectory.findDisplayInfo(listOf(message.senderId))[message.senderId]?.displayName
        val conversation by lazy { conversationRepository.findById(message.conversationId) }

        val wsMessage = WsMessage.NewMessage(
            messageId = message.id.toString(),
            conversationId = message.conversationId.toString(),
            senderId = message.senderId.toString(),
            senderName = senderDisplayName,
            content = message.content,
            contentType = com.muhabbet.shared.model.ContentType.valueOf(message.contentType.name),
            replyToId = message.replyToId?.toString(),
            // Sealed on the wire, not in the UI: a view-once frame names the flag and withholds the
            // blob URL, which is released once by POST /messages/{id}/view-once. See MessageMapper.
            mediaUrl = if (message.viewOnce) null else message.mediaUrl,
            thumbnailUrl = if (message.viewOnce) null else message.thumbnailUrl,
            serverTimestamp = message.serverTimestamp.toEpochMilli(),
            forwardedFrom = message.forwardedFrom?.toString(),
            viewOnce = message.viewOnce
        )

        val json = wsJson.encodeToString<WsMessage>(wsMessage)

        // Collected in the loop and pushed after it — one device query for the whole group (#492).
        // Kept in step with RedisMessageBroadcaster by hand, as the note below says.
        val pushTo = mutableListOf<UUID>()

        recipients.forEach { member ->
            val recipientId = member.userId
            if (sessionManager.isOnline(recipientId)) {
                sessionManager.sendToUser(recipientId, json)
                log.debug("Message {} delivered to online user {}", message.id, recipientId)
            } else {
                log.debug("User {} offline, message {} queued in DB", recipientId, message.id)
            }

            // A push is withheld for two independent reasons: this member has muted the
            // conversation (#571), or is foregrounded on it right now (#618) — a live socket
            // elsewhere, screen off, or the app merely backgrounded all still get one. Checked
            // outside the isOnline branch above, not inside its `else`: an online recipient reading
            // a different chat needs exactly the same push an offline one does. Kept in step with
            // RedisMessageBroadcaster's identical check by hand — #469 was this same pair of
            // broadcasters drifting apart once already.
            if (!member.isMuted() && !sessionManager.isViewingConversation(recipientId, message.conversationId)) {
                pushTo += recipientId
            }
        }

        // Guarded so the lazy `conversation` is not forced when no push is owed — see the twin
        // comment in RedisMessageBroadcaster.
        if (pushTo.isNotEmpty()) {
            offlinePushSender.sendToAll(pushTo, message, senderDisplayName, conversation)
        }
    }

    override fun broadcastToUsers(recipientIds: List<UUID>, message: WsMessage) {
        val json = wsJson.encodeToString<WsMessage>(message)
        recipientIds.forEach { recipientId ->
            if (sessionManager.isOnline(recipientId)) {
                sessionManager.sendToUser(recipientId, json)
            }
        }
    }

    override fun broadcastStatusUpdate(messageId: UUID, conversationId: UUID, readerId: UUID, senderId: UUID, status: DeliveryStatus) {
        val wsStatus = when (status) {
            DeliveryStatus.DELIVERED -> MessageStatus.DELIVERED
            DeliveryStatus.READ -> MessageStatus.READ
            DeliveryStatus.SENT -> MessageStatus.SENT
        }

        val wsMessage = WsMessage.StatusUpdate(
            messageId = messageId.toString(),
            conversationId = conversationId.toString(),
            userId = readerId.toString(),
            status = wsStatus,
            timestamp = System.currentTimeMillis()
        )

        val json = wsJson.encodeToString<WsMessage>(wsMessage)

        // Send to the original SENDER so they see delivery/read status
        sessionManager.sendToUser(senderId, json)
    }
}

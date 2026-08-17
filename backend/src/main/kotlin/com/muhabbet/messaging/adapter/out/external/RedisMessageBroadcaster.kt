package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.service.PushNotificationComposer
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Redis Pub/Sub backed message broadcaster for horizontal scaling.
 * When multiple backend instances are running behind a load balancer,
 * a WebSocket recipient may be connected to a DIFFERENT instance than the sender.
 *
 * Flow:
 * 1. Instance A receives message from sender
 * 2. Instance A publishes to Redis channel "ws:user:{recipientId}"
 * 3. ALL instances receive the pub/sub message
 * 4. Instance B (which holds the recipient's WS session) delivers it
 *
 * For single-instance deployments, this still works — just no cross-instance routing needed.
 */
@Primary
@Component
class RedisMessageBroadcaster(
    private val sessionManager: WebSocketSessionManager,
    private val pushNotificationPort: PushNotificationPort,
    private val deviceRepository: DeviceRepository,
    private val redisTemplate: StringRedisTemplate,
    private val userDirectory: UserDirectoryPort,
    private val conversationRepository: ConversationRepository,
    private val pushComposer: PushNotificationComposer
) : MessageBroadcaster {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val WS_CHANNEL_PREFIX = "ws:broadcast:"
    }

    override fun broadcastMessage(message: Message, recipientIds: List<UUID>) {
        val contentType = try {
            com.muhabbet.shared.model.ContentType.valueOf(message.contentType.name)
        } catch (e: Exception) {
            com.muhabbet.shared.model.ContentType.TEXT
        }

        // One lookup, reused by the socket payload and by every offline recipient's push. Resolved
        // before the loop so a group of fifty does not run fifty identical queries.
        val senderName = userDirectory.findDisplayInfo(listOf(message.senderId))[message.senderId]?.displayName

        // Only the push title needs the conversation, and most broadcasts reach nobody offline, so
        // this stays off the hot path until an offline recipient actually asks for it.
        val conversation by lazy { conversationRepository.findById(message.conversationId) }

        val newMessage = WsMessage.NewMessage(
            messageId = message.id.toString(),
            conversationId = message.conversationId.toString(),
            senderId = message.senderId.toString(),
            senderName = senderName,
            content = message.content,
            contentType = contentType,
            replyToId = message.replyToId?.toString(),
            // Sealed on the wire, not in the UI: a view-once frame names the flag and withholds the
            // blob URL, which is released once by POST /messages/{id}/view-once. See MessageMapper.
            mediaUrl = if (message.viewOnce) null else message.mediaUrl,
            thumbnailUrl = if (message.viewOnce) null else message.thumbnailUrl,
            serverTimestamp = message.serverTimestamp.toEpochMilli(),
            forwardedFrom = message.forwardedFrom?.toString(),
            viewOnce = message.viewOnce
        )
        val json = wsJson.encodeToString<WsMessage>(newMessage)

        recipientIds.forEach { recipientId ->
            if (sessionManager.isOnline(recipientId)) {
                // Local delivery (recipient is on this instance)
                sessionManager.sendToUser(recipientId, json)
            } else {
                // Publish to Redis for cross-instance delivery
                try {
                    redisTemplate.convertAndSend("$WS_CHANNEL_PREFIX${recipientId}", json)
                } catch (e: Exception) {
                    log.warn("Redis pub/sub publish failed for userId={}: {}", recipientId, e.message)
                }

                // Also send push notification for offline users
                try {
                    // recipientLocale is omitted because no device row carries one yet; the
                    // composer falls back to Turkish and says so. Follow-up on #469.
                    val push = pushComposer.compose(message, senderName, conversation)
                    val devices = deviceRepository.findByUserId(recipientId)
                    devices.filter { !it.pushToken.isNullOrBlank() }.forEach { device ->
                        device.pushToken?.let { token -> pushNotificationPort.sendPush(token, push) }
                    }
                } catch (e: Exception) {
                    log.warn("Push notification failed for userId={}: {}", recipientId, e.message)
                }
            }
        }
    }

    override fun broadcastStatusUpdate(
        messageId: UUID, conversationId: UUID, readerId: UUID, senderId: UUID, status: DeliveryStatus
    ) {
        val statusEnum = when (status) {
            DeliveryStatus.DELIVERED -> MessageStatus.DELIVERED
            DeliveryStatus.READ -> MessageStatus.READ
            else -> MessageStatus.SENT
        }

        val update = WsMessage.StatusUpdate(
            messageId = messageId.toString(),
            conversationId = conversationId.toString(),
            userId = readerId.toString(),
            status = statusEnum,
            timestamp = System.currentTimeMillis()
        )
        val json = wsJson.encodeToString<WsMessage>(update)

        // Deliver to sender (local or via Redis)
        if (sessionManager.isOnline(senderId)) {
            sessionManager.sendToUser(senderId, json)
        } else {
            try {
                redisTemplate.convertAndSend("$WS_CHANNEL_PREFIX${senderId}", json)
            } catch (e: Exception) {
                log.debug("Redis pub/sub for status update failed: {}", e.message)
            }
        }
    }

    override fun broadcastToUsers(recipientIds: List<UUID>, message: WsMessage) {
        val json = wsJson.encodeToString(message)
        recipientIds.forEach { userId ->
            if (sessionManager.isOnline(userId)) {
                sessionManager.sendToUser(userId, json)
            } else {
                try {
                    redisTemplate.convertAndSend("$WS_CHANNEL_PREFIX${userId}", json)
                } catch (e: Exception) {
                    log.debug("Redis pub/sub broadcast failed for userId={}: {}", userId, e.message)
                }
            }
        }
    }
}

/**
 * Redis Pub/Sub listener configuration.
 * Each backend instance subscribes to channels for ALL users with active WS sessions
 * on that instance. When a message arrives via Redis, it's delivered to the local WS session.
 */
@Component
class RedisBroadcastListener(
    private val sessionManager: WebSocketSessionManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handleMessage(message: String, channel: String) {
        // Extract userId from channel name "ws:broadcast:{userId}"
        val userIdStr = channel.removePrefix(RedisMessageBroadcaster.WS_CHANNEL_PREFIX)
        val userId = try {
            UUID.fromString(userIdStr)
        } catch (e: Exception) {
            log.warn("Invalid userId in Redis channel: {}", channel)
            return
        }

        // Only deliver if user has an active session on THIS instance
        if (sessionManager.isOnline(userId)) {
            sessionManager.sendToUser(userId, message)
        }
    }
}

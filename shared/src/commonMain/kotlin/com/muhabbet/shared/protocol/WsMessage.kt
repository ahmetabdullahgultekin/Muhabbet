package com.muhabbet.shared.protocol

import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.model.PresenceStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * All WebSocket messages exchanged between client and server.
 * This sealed hierarchy is the SINGLE SOURCE OF TRUTH for the WS protocol.
 * Both backend and mobile use these exact types.
 */
@Serializable
sealed class WsMessage {

    // ─── Client → Server ─────────────────────────────────

    /** Client sends a new message */
    @Serializable
    @SerialName("message.send")
    data class SendMessage(
        val requestId: String,              // client-generated, for ACK correlation
        val messageId: String,              // UUIDv7, idempotency key
        val conversationId: String,
        val content: String,
        val contentType: ContentType = ContentType.TEXT,
        val replyToId: String? = null,
        val mediaUrl: String? = null
    ) : WsMessage()

    /** Client acknowledges received message (delivered/read) */
    @Serializable
    @SerialName("message.ack")
    data class AckMessage(
        val messageId: String,
        val conversationId: String,
        val status: MessageStatus           // DELIVERED or READ
    ) : WsMessage()

    /** Client typing indicator */
    @Serializable
    @SerialName("presence.typing")
    data class TypingIndicator(
        val conversationId: String,
        val isTyping: Boolean
    ) : WsMessage()

    /** Client requests to go online (sent on connect) */
    @Serializable
    @SerialName("presence.online")
    data object GoOnline : WsMessage()

    /** Client heartbeat / ping */
    @Serializable
    @SerialName("ping")
    data object Ping : WsMessage()

    // ─── Server → Client ─────────────────────────────────

    /** Server delivers a new message to recipient */
    @Serializable
    @SerialName("message.new")
    data class NewMessage(
        val messageId: String,
        val conversationId: String,
        val senderId: String,
        val senderName: String?,
        val content: String,
        val contentType: ContentType,
        val replyToId: String? = null,
        val mediaUrl: String? = null,
        val thumbnailUrl: String? = null,
        val serverTimestamp: Long            // epoch millis
    ) : WsMessage()

    /** Server notifies sender about delivery status change */
    @Serializable
    @SerialName("message.status")
    data class StatusUpdate(
        val messageId: String,
        val conversationId: String,
        val userId: String,                 // who triggered the status change
        val status: MessageStatus,
        val timestamp: Long
    ) : WsMessage()

    /** Server ACK for client's SendMessage */
    @Serializable
    @SerialName("ack")
    data class ServerAck(
        val requestId: String,              // correlates to SendMessage.requestId
        val messageId: String,
        val status: AckStatus,
        val serverTimestamp: Long? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null
    ) : WsMessage()

    /** Server notifies about presence changes */
    @Serializable
    @SerialName("presence.update")
    data class PresenceUpdate(
        val userId: String,
        val conversationId: String? = null, // null = global, set = typing in specific convo
        val status: PresenceStatus,
        val lastSeenAt: Long? = null
    ) : WsMessage()

    /** Server heartbeat response */
    @Serializable
    @SerialName("pong")
    data object Pong : WsMessage()

    /** Server error (non-ACK errors like auth failure) */
    @Serializable
    @SerialName("error")
    data class Error(
        val code: String,
        val message: String
    ) : WsMessage()
}

@Serializable
enum class AckStatus {
    OK,
    ERROR
}

/**
 * JSON serializer configured for the WS protocol.
 * Use this SAME instance on both backend and mobile.
 */
val wsJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = false
}

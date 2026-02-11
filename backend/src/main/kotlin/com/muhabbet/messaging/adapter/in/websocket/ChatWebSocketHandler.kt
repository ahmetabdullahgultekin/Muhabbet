package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.shared.protocol.AckStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.JwtProvider
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant
import java.util.UUID

@Component
class ChatWebSocketHandler(
    private val jwtProvider: JwtProvider,
    private val sessionManager: WebSocketSessionManager,
    private val sendMessageUseCase: SendMessageUseCase,
    private val updateDeliveryStatusUseCase: UpdateDeliveryStatusUseCase
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val token = extractToken(session)
        if (token == null) {
            sendError(session, "AUTH_TOKEN_INVALID", "Missing token query parameter")
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }

        val claims = jwtProvider.validateToken(token)
        if (claims == null) {
            sendError(session, "AUTH_TOKEN_INVALID", "Invalid or expired JWT")
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }

        session.attributes["userId"] = claims.userId
        session.attributes["deviceId"] = claims.deviceId
        sessionManager.register(claims.userId, session)
        log.info("WebSocket connected: userId={}", claims.userId)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = session.attributes["userId"] as? UUID ?: return

        val wsMessage = try {
            wsJson.decodeFromString<WsMessage>(message.payload)
        } catch (e: Exception) {
            sendError(session, "VALIDATION_ERROR", "Invalid message format: ${e.message}")
            return
        }

        when (wsMessage) {
            is WsMessage.SendMessage -> handleSendMessage(session, userId, wsMessage)
            is WsMessage.AckMessage -> handleAckMessage(userId, wsMessage)
            is WsMessage.TypingIndicator -> handleTypingIndicator(userId, wsMessage)
            is WsMessage.GoOnline -> log.debug("User {} went online", userId)
            is WsMessage.Ping -> sendPong(session)
            else -> sendError(session, "VALIDATION_ERROR", "Unexpected message type from client")
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.attributes["userId"] as? UUID
        sessionManager.unregister(session)
        log.info("WebSocket disconnected: userId={}, status={}", userId, status)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.warn("WebSocket transport error: sessionId={}, error={}", session.id, exception.message)
        sessionManager.unregister(session)
    }

    private fun handleSendMessage(session: WebSocketSession, senderId: UUID, msg: WsMessage.SendMessage) {
        try {
            val contentType = try {
                ContentType.valueOf(msg.contentType.name)
            } catch (e: Exception) {
                ContentType.TEXT
            }

            val message = sendMessageUseCase.sendMessage(
                SendMessageCommand(
                    messageId = UUID.fromString(msg.messageId),
                    conversationId = UUID.fromString(msg.conversationId),
                    senderId = senderId,
                    content = msg.content,
                    contentType = contentType,
                    replyToId = msg.replyToId?.let { UUID.fromString(it) },
                    mediaUrl = msg.mediaUrl,
                    clientTimestamp = Instant.now()
                )
            )

            // Send ACK to sender
            val ack = WsMessage.ServerAck(
                requestId = msg.requestId,
                messageId = msg.messageId,
                status = AckStatus.OK,
                serverTimestamp = message.serverTimestamp.toEpochMilli()
            )
            session.sendMessage(TextMessage(wsJson.encodeToString<WsMessage>(ack)))

        } catch (e: Exception) {
            val ack = WsMessage.ServerAck(
                requestId = msg.requestId,
                messageId = msg.messageId,
                status = AckStatus.ERROR,
                errorCode = "MSG_SEND_FAILED",
                errorMessage = e.message
            )
            session.sendMessage(TextMessage(wsJson.encodeToString<WsMessage>(ack)))
            log.warn("Failed to send message: {}", e.message)
        }
    }

    private fun handleAckMessage(userId: UUID, msg: WsMessage.AckMessage) {
        val status = when (msg.status) {
            com.muhabbet.shared.model.MessageStatus.DELIVERED -> DeliveryStatus.DELIVERED
            com.muhabbet.shared.model.MessageStatus.READ -> DeliveryStatus.READ
            else -> return
        }
        if (status == DeliveryStatus.READ) {
            // Mark ALL messages in the conversation as read (not just one)
            updateDeliveryStatusUseCase.markConversationRead(
                UUID.fromString(msg.conversationId), userId
            )
        } else {
            updateDeliveryStatusUseCase.updateStatus(UUID.fromString(msg.messageId), userId, status)
        }
    }

    private fun handleTypingIndicator(userId: UUID, msg: WsMessage.TypingIndicator) {
        // TODO: broadcast typing indicator to conversation members
        log.debug("Typing indicator: userId={}, conv={}, isTyping={}", userId, msg.conversationId, msg.isTyping)
    }

    private fun sendPong(session: WebSocketSession) {
        val pong = WsMessage.Pong
        session.sendMessage(TextMessage(wsJson.encodeToString<WsMessage>(pong)))
    }

    private fun sendError(session: WebSocketSession, code: String, message: String) {
        val error = WsMessage.Error(code = code, message = message)
        if (session.isOpen) {
            session.sendMessage(TextMessage(wsJson.encodeToString<WsMessage>(error)))
        }
    }

    private fun extractToken(session: WebSocketSession): String? {
        val uri = session.uri ?: return null
        val params = UriComponentsBuilder.fromUri(uri).build().queryParams
        return params.getFirst("token")
    }
}

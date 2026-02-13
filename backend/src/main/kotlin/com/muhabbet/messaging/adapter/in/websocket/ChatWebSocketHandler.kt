package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.CallStatus
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.messaging.domain.service.CallBusyException
import com.muhabbet.messaging.domain.service.CallSignalingService
import com.muhabbet.shared.model.PresenceStatus
import com.muhabbet.shared.protocol.AckStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.JwtProvider
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
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
    private val updateDeliveryStatusUseCase: UpdateDeliveryStatusUseCase,
    private val conversationRepository: ConversationRepository,
    private val presencePort: PresencePort,
    private val userRepository: UserRepository,
    private val callSignalingService: CallSignalingService
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

        // Set online in Redis and broadcast presence
        presencePort.setOnline(claims.userId)
        broadcastPresence(claims.userId, PresenceStatus.ONLINE)
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
            is WsMessage.GoOnline -> {
                presencePort.setOnline(userId)
                log.debug("User {} went online", userId)
            }
            is WsMessage.Ping -> {
                presencePort.setOnline(userId)
                sendPong(session)
            }
            is WsMessage.CallInitiate -> handleCallInitiate(session, userId, wsMessage)
            is WsMessage.CallAnswer -> handleCallAnswer(session, userId, wsMessage)
            is WsMessage.CallIceCandidate -> handleCallIce(userId, wsMessage)
            is WsMessage.CallEnd -> handleCallEnd(userId, wsMessage)
            else -> sendError(session, "VALIDATION_ERROR", "Unexpected message type from client")
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        // Read userId from our own map BEFORE unregister clears it
        val userId = sessionManager.getUserId(session)
        sessionManager.unregister(session)

        // If no remaining sessions, mark offline
        if (userId != null && !sessionManager.isOnline(userId)) {
            presencePort.setOffline(userId)
            try {
                userRepository.updateLastSeenAt(userId, Instant.now())
            } catch (e: Exception) {
                log.warn("Failed to persist last_seen_at for {}: {}", userId, e.message)
            }
            broadcastPresence(userId, PresenceStatus.OFFLINE)
        }
        log.info("WebSocket disconnected: userId={}, status={}", userId, status)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        val userId = sessionManager.getUserId(session)
        log.warn("WebSocket transport error: sessionId={}, userId={}, error={}", session.id, userId, exception.message)
        sessionManager.unregister(session)

        if (userId != null && !sessionManager.isOnline(userId)) {
            presencePort.setOffline(userId)
            try {
                userRepository.updateLastSeenAt(userId, Instant.now())
            } catch (e: Exception) {
                log.warn("Failed to persist last_seen_at for {}: {}", userId, e.message)
            }
            broadcastPresence(userId, PresenceStatus.OFFLINE)
        }
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
                    thumbnailUrl = msg.thumbnailUrl,
                    clientTimestamp = Instant.now(),
                    forwardedFrom = msg.forwardedFrom?.let { try { UUID.fromString(it) } catch (_: Exception) { null } }
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
            // Bulk-update ALL messages in the conversation as read in DB
            updateDeliveryStatusUseCase.markConversationRead(
                UUID.fromString(msg.conversationId), userId
            )
        }
        // Always broadcast StatusUpdate for the specific message to the sender
        updateDeliveryStatusUseCase.updateStatus(UUID.fromString(msg.messageId), userId, status)
    }

    private fun handleTypingIndicator(userId: UUID, msg: WsMessage.TypingIndicator) {
        val conversationId = UUID.fromString(msg.conversationId)
        val members = conversationRepository.findMembersByConversationId(conversationId)
        val recipientIds = members.map { it.userId }.filter { it != userId }

        val status = if (msg.isTyping) PresenceStatus.TYPING else PresenceStatus.ONLINE
        val presenceUpdate = WsMessage.PresenceUpdate(
            userId = userId.toString(),
            conversationId = msg.conversationId,
            status = status
        )
        val json = wsJson.encodeToString<WsMessage>(presenceUpdate)

        recipientIds.forEach { recipientId ->
            if (sessionManager.isOnline(recipientId)) {
                sessionManager.sendToUser(recipientId, json)
            }
        }
    }

    // ─── Call Signaling Handlers ────────────────────────────

    private fun handleCallInitiate(session: WebSocketSession, callerId: UUID, msg: WsMessage.CallInitiate) {
        val calleeId = try {
            UUID.fromString(msg.targetUserId)
        } catch (e: Exception) {
            sendError(session, "CALL_INVALID_TARGET", "Invalid target user ID")
            return
        }

        // Map shared CallType to backend domain CallType
        val callType = com.muhabbet.messaging.domain.model.CallType.valueOf(msg.callType.name)

        try {
            callSignalingService.initiateCall(msg.callId, callerId, calleeId, callType)
        } catch (e: CallBusyException) {
            // Send call.end with BUSY reason back to caller
            val busy = WsMessage.CallEnd(callId = msg.callId, reason = com.muhabbet.shared.model.CallEndReason.BUSY)
            session.sendMessage(TextMessage(wsJson.encodeToString<WsMessage>(busy)))
            return
        }

        // Check if callee is online
        if (!sessionManager.isOnline(calleeId)) {
            // Callee offline — end call with MISSED
            callSignalingService.endCall(msg.callId, CallStatus.MISSED)
            val missed = WsMessage.CallEnd(callId = msg.callId, reason = com.muhabbet.shared.model.CallEndReason.MISSED)
            session.sendMessage(TextMessage(wsJson.encodeToString<WsMessage>(missed)))
            return
        }

        // Lookup caller name for the incoming notification
        val callerName = userRepository.findById(callerId)?.displayName

        // Forward call.incoming to callee
        val incoming = WsMessage.CallIncoming(
            callId = msg.callId,
            callerId = callerId.toString(),
            callerName = callerName,
            callType = msg.callType
        )
        sessionManager.sendToUser(calleeId, wsJson.encodeToString<WsMessage>(incoming))

        // Also forward the SDP offer if present (caller's offer → callee)
        if (msg.sdpOffer != null) {
            val initiateForward = WsMessage.CallInitiate(
                callId = msg.callId,
                targetUserId = msg.targetUserId,
                callType = msg.callType,
                sdpOffer = msg.sdpOffer
            )
            sessionManager.sendToUser(calleeId, wsJson.encodeToString<WsMessage>(initiateForward))
        }

        log.info("Call initiated: callId={}, caller={}, callee={}", msg.callId, callerId, calleeId)
    }

    private fun handleCallAnswer(session: WebSocketSession, userId: UUID, msg: WsMessage.CallAnswer) {
        val callSession = callSignalingService.getCall(msg.callId)
        if (callSession == null) {
            sendError(session, "CALL_NOT_FOUND", "Call ${msg.callId} not found")
            return
        }

        val otherParty = callSignalingService.getOtherParty(msg.callId, userId) ?: return

        if (msg.accepted) {
            callSignalingService.answerCall(msg.callId)
        } else {
            callSignalingService.endCall(msg.callId, CallStatus.DECLINED)
        }

        // Forward the answer to the other party
        val json = wsJson.encodeToString<WsMessage>(msg)
        sessionManager.sendToUser(otherParty, json)

        log.info("Call answer: callId={}, userId={}, accepted={}", msg.callId, userId, msg.accepted)
    }

    private fun handleCallIce(userId: UUID, msg: WsMessage.CallIceCandidate) {
        val otherParty = callSignalingService.getOtherParty(msg.callId, userId) ?: return

        // Forward ICE candidate to the other party
        val json = wsJson.encodeToString<WsMessage>(msg)
        sessionManager.sendToUser(otherParty, json)
    }

    private fun handleCallEnd(userId: UUID, msg: WsMessage.CallEnd) {
        val callSession = callSignalingService.getCall(msg.callId) ?: return
        val otherParty = callSignalingService.getOtherParty(msg.callId, userId)

        // Map shared CallEndReason to domain CallStatus
        val status = when (msg.reason) {
            com.muhabbet.shared.model.CallEndReason.DECLINED -> CallStatus.DECLINED
            com.muhabbet.shared.model.CallEndReason.MISSED -> CallStatus.MISSED
            else -> CallStatus.ENDED
        }

        callSignalingService.endCall(msg.callId, status)

        // Forward call.end to the other party
        if (otherParty != null) {
            val json = wsJson.encodeToString<WsMessage>(msg)
            sessionManager.sendToUser(otherParty, json)
        }

        log.info("Call ended: callId={}, userId={}, reason={}", msg.callId, userId, msg.reason)
    }

    // ─── Messaging Helpers ────────────────────────────────────

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

    private fun broadcastPresence(userId: UUID, status: PresenceStatus) {
        val lastSeenAt = if (status == PresenceStatus.OFFLINE) System.currentTimeMillis() else null
        val presenceUpdate = WsMessage.PresenceUpdate(
            userId = userId.toString(),
            status = status,
            lastSeenAt = lastSeenAt
        )
        val json = wsJson.encodeToString<WsMessage>(presenceUpdate)

        // Find all conversations this user belongs to, then notify online members
        val conversations = conversationRepository.findConversationsByUserId(userId)
        val notifiedUsers = mutableSetOf<UUID>()

        conversations.forEach { conv ->
            val members = conversationRepository.findMembersByConversationId(conv.id)
            members.forEach { member ->
                if (member.userId != userId && member.userId !in notifiedUsers && sessionManager.isOnline(member.userId)) {
                    sessionManager.sendToUser(member.userId, json)
                    notifiedUsers.add(member.userId)
                }
            }
        }
    }

    private fun extractToken(session: WebSocketSession): String? {
        val uri = session.uri ?: return null
        val params = UriComponentsBuilder.fromUri(uri).build().queryParams
        return params.getFirst("token")
    }
}

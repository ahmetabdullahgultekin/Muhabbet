package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.port.`in`.RecordLastSeenUseCase
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.CallStatus
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.messaging.domain.service.CallBusyException
import com.muhabbet.messaging.domain.port.out.CallRoomProvider
import com.muhabbet.messaging.domain.service.CallSignalingService
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.PresenceStatus
import com.muhabbet.shared.protocol.AckStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.security.WebSocketRateLimiter
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PongMessage
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
    private val recordLastSeenUseCase: RecordLastSeenUseCase,
    private val callSignalingService: CallSignalingService,
    private val callRoomProvider: CallRoomProvider,
    private val webSocketRateLimiter: WebSocketRateLimiter,
    private val blockPolicy: BlockPolicyPort
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val token = extractToken(session)
        if (token == null) {
            sendError(session, ErrorCode.AUTH_TOKEN_INVALID, "Missing token query parameter")
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }

        val claims = jwtProvider.validateToken(token)
        if (claims == null) {
            sendError(session, ErrorCode.AUTH_TOKEN_INVALID, "Invalid or expired JWT")
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
        // Before anything can reject this frame (rate limit, bad JSON, unknown type): it arrived,
        // so the peer is alive. Liveness must not depend on the frame being well-formed or wanted.
        sessionManager.touch(session)

        val userId = session.attributes["userId"] as? UUID ?: return

        // Per-connection rate limiting
        if (!webSocketRateLimiter.allowMessage(userId)) {
            sendError(session, ErrorCode.RATE_LIMITED, "Too many messages, please slow down")
            return
        }

        val wsMessage = try {
            wsJson.decodeFromString<WsMessage>(message.payload)
        } catch (e: Exception) {
            sendError(session, ErrorCode.VALIDATION_ERROR, "Invalid message format: ${e.message}")
            return
        }

        // Nothing a client can put in a frame may cost it its connection.
        //
        // Only handleSendMessage had a catch of its own; every other branch ran bare. That is not
        // theoretical tidiness: handleTypingIndicator calls UUID.fromString on a client-supplied
        // conversationId with no guard, so one malformed id threw IllegalArgumentException straight
        // out of here into the container, which closes the socket. The user's chat stops working
        // and the cause is a typing indicator.
        //
        // A refusal the domain made keeps its own code, exactly as on the send path; anything else
        // is INTERNAL_ERROR and gets a stack trace, so this catch cannot quietly become the place
        // real faults go to die.
        try {
            when (wsMessage) {
                is WsMessage.SendMessage -> handleSendMessage(session, userId, wsMessage)
                is WsMessage.AckMessage -> handleAckMessage(userId, wsMessage)
                is WsMessage.TypingIndicator -> handleTypingIndicator(userId, wsMessage)
                is WsMessage.GoOnline -> {
                    presencePort.setOnline(userId)
                    log.debug("User {} went online", userId)
                }
                is WsMessage.ConversationFocus -> handleConversationFocus(userId, wsMessage)
                is WsMessage.Ping -> {
                    presencePort.setOnline(userId)
                    sendPong(session)
                }
                is WsMessage.CallInitiate -> handleCallInitiate(session, userId, wsMessage)
                is WsMessage.CallAnswer -> handleCallAnswer(session, userId, wsMessage)
                is WsMessage.CallIceCandidate -> handleCallIce(userId, wsMessage)
                is WsMessage.CallEnd -> handleCallEnd(userId, wsMessage)
                else -> sendError(session, ErrorCode.VALIDATION_ERROR, "Unexpected message type from client")
            }
        } catch (e: BusinessException) {
            sendError(session, e.errorCode, e.message)
            log.warn("Frame rejected: type={}, {} - {}", wsMessage::class.simpleName, e.errorCode, e.message)
        } catch (e: Exception) {
            sendError(session, ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage)
            log.error("Unexpected failure handling frame: type={}", wsMessage::class.simpleName, e)
        }
    }

    /**
     * The reply to the ping frames [WebSocketSessionManager.reapStaleSessions] sends. It is the only
     * proof of life we get from a client that is backgrounded and has stopped sending its own
     * heartbeat, so it counts as inbound traffic exactly like a text frame does.
     */
    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
        sessionManager.touch(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        // Read userId from our own map BEFORE unregister clears it
        val userId = sessionManager.getUserId(session)
        sessionManager.unregister(session)

        // If no remaining sessions, mark offline and clean up
        if (userId != null && !sessionManager.isOnline(userId)) {
            webSocketRateLimiter.removeUser(userId)
            goOffline(userId)
        }
        log.info("WebSocket disconnected: userId={}, status={}", userId, status)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        val userId = sessionManager.getUserId(session)
        log.warn("WebSocket transport error: sessionId={}, userId={}, error={}", session.id, userId, exception.message)
        sessionManager.unregister(session)

        if (userId != null && !sessionManager.isOnline(userId)) {
            goOffline(userId)
        }
    }

    /**
     * Everything that has to happen once a user's last socket is gone, in one place because the two
     * ways a socket can end — a close frame and a transport error — must not drift apart. They had
     * two copies of this, identical down to the log message.
     *
     * The `last_seen_at` write goes through [RecordLastSeenUseCase] rather than straight to the
     * repository. That is the fix for #402: the write is a `@Modifying` query, this thread has no
     * transaction, and calling the repository from here threw `No active transaction for update or
     * delete query` on every single disconnect. The transaction now belongs to the service behind
     * that port, and it only exists because this call crosses a Spring proxy — moving the write back
     * into a private method here, or into any call this class makes on itself, brings the bug back
     * unchanged.
     *
     * The failure is logged at ERROR with the exception, not warned about with `e.message`. A write
     * that had never once succeeded produced a WARN line that read like noise for months; if this
     * ever fails again it should look like what it is.
     */
    private fun goOffline(userId: UUID) {
        presencePort.setOffline(userId)
        try {
            recordLastSeenUseCase.recordLastSeen(userId, Instant.now())
        } catch (e: Exception) {
            log.error("Failed to persist last_seen_at for {}", userId, e)
        }
        broadcastPresence(userId, PresenceStatus.OFFLINE)
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
                    messageId = parseId(msg.messageId, "messageId"),
                    conversationId = parseId(msg.conversationId, "conversationId"),
                    senderId = senderId,
                    content = msg.content,
                    contentType = contentType,
                    replyToId = msg.replyToId?.let { parseId(it, "replyToId") },
                    mediaUrl = msg.mediaUrl,
                    thumbnailUrl = msg.thumbnailUrl,
                    clientTimestamp = Instant.now(),
                    forwardedFrom = msg.forwardedFrom?.let { try { UUID.fromString(it) } catch (_: Exception) { null } },
                    viewOnce = msg.viewOnce,
                    scheduledAt = msg.scheduledAt?.let { Instant.ofEpochMilli(it) }
                )
            )

            // Send ACK to sender
            val ack = WsMessage.ServerAck(
                requestId = msg.requestId,
                messageId = msg.messageId,
                status = AckStatus.OK,
                serverTimestamp = message.serverTimestamp.toEpochMilli()
            )
            sessionManager.send(session, wsJson.encodeToString<WsMessage>(ack))

        } catch (e: BusinessException) {
            // The refusal the domain actually made, not a label for all of them. `MSG_SEND_FAILED`
            // was a string invented here — it is not an ErrorCode and never has been — and it
            // reached the client for "too long", "not a member", "announcement only", "duplicate"
            // and a genuine fault alike. The client's job is to map a code to a localized string,
            // which it cannot do when five causes share one code; the fallback was either a generic
            // sentence or the server's Turkish text shown to someone reading the app in English.
            // The REST path never had this problem — GlobalExceptionHandler answers with
            // `errorCode.name` — so this is the WebSocket path being brought into line with it.
            respondSendFailed(session, msg, e.errorCode, e.message)
            log.warn("Message rejected: {} - {}", e.errorCode, e.message)
        } catch (e: Exception) {
            // A genuine fault, and kept distinct from a refusal on both channels: the client is told
            // INTERNAL_ERROR rather than something from the message domain, and the log carries the
            // stack trace instead of just `e.message`. GlobalExceptionHandler draws the same line
            // for the same reason — so this line stays worth paging on.
            respondSendFailed(session, msg, ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage)
            log.error("Unexpected failure sending message: conv={}", msg.conversationId, e)
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
                parseId(msg.conversationId, "conversationId"), userId
            )
        }
        // Always broadcast StatusUpdate for the specific message to the sender
        updateDeliveryStatusUseCase.updateStatus(parseId(msg.messageId, "messageId"), userId, status)
    }

    /**
     * Records which conversation, if any, [userId] is foregrounded on — the signal
     * [com.muhabbet.messaging.adapter.out.external.RedisMessageBroadcaster] needs to suppress a push
     * only for the exact chat being looked at (#618). A malformed or absent id degrades to "none"
     * rather than erroring: this is a best-effort presence hint, not a request that can fail.
     */
    private fun handleConversationFocus(userId: UUID, msg: WsMessage.ConversationFocus) {
        val conversationId = msg.conversationId?.let { raw ->
            try {
                UUID.fromString(raw)
            } catch (e: Exception) {
                null
            }
        }
        sessionManager.setActiveConversation(userId, conversationId)
    }

    private fun handleTypingIndicator(userId: UUID, msg: WsMessage.TypingIndicator) {
        val conversationId = parseId(msg.conversationId, "conversationId")
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
            sendError(session, ErrorCode.CALL_INVALID_TARGET, "Invalid target user ID")
            return
        }

        // Map shared CallType to backend domain CallType
        val callType = com.muhabbet.messaging.domain.model.CallType.valueOf(msg.callType.name)

        try {
            callSignalingService.initiateCall(msg.callId, callerId, calleeId, callType)
        } catch (e: CallBusyException) {
            // Send call.end with BUSY reason back to caller
            val busy = WsMessage.CallEnd(callId = msg.callId, reason = com.muhabbet.shared.model.CallEndReason.BUSY)
            sessionManager.send(session, wsJson.encodeToString<WsMessage>(busy))
            return
        }

        // Check if callee is online
        if (!sessionManager.isOnline(calleeId)) {
            // Callee offline — end call with MISSED
            callSignalingService.endCall(msg.callId, CallStatus.MISSED)
            val missed = WsMessage.CallEnd(callId = msg.callId, reason = com.muhabbet.shared.model.CallEndReason.MISSED)
            sessionManager.send(session, wsJson.encodeToString<WsMessage>(missed))
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
            sendError(session, ErrorCode.CALL_NOT_FOUND, "Call ${msg.callId} not found")
            return
        }

        val otherParty = callSignalingService.getOtherParty(msg.callId, userId) ?: return

        if (msg.accepted) {
            callSignalingService.answerCall(msg.callId)

            // Create LiveKit room and send tokens to both participants
            try {
                val room = callRoomProvider.createRoom(msg.callId, otherParty, userId)
                if (room.serverUrl.isNotBlank()) {
                    val callerName = userRepository.findById(otherParty)?.displayName
                    val calleeName = userRepository.findById(userId)?.displayName

                    val callerToken = callRoomProvider.generateParticipantToken(room.roomName, otherParty, callerName)
                    val calleeToken = callRoomProvider.generateParticipantToken(room.roomName, userId, calleeName)

                    // Send room info to caller
                    val callerRoomInfo = WsMessage.CallRoomInfo(
                        callId = msg.callId,
                        serverUrl = room.serverUrl,
                        token = callerToken,
                        roomName = room.roomName
                    )
                    sessionManager.sendToUser(otherParty, wsJson.encodeToString<WsMessage>(callerRoomInfo))

                    // Send room info to callee
                    val calleeRoomInfo = WsMessage.CallRoomInfo(
                        callId = msg.callId,
                        serverUrl = room.serverUrl,
                        token = calleeToken,
                        roomName = room.roomName
                    )
                    sessionManager.send(session, wsJson.encodeToString<WsMessage>(calleeRoomInfo))

                    log.info("LiveKit room created: callId={}, room={}", msg.callId, room.roomName)
                }
            } catch (e: Exception) {
                log.warn("Failed to create LiveKit room for callId={}: {}", msg.callId, e.message)
            }
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

        // Close the LiveKit room
        try {
            callRoomProvider.closeRoom("call-${msg.callId}")
        } catch (e: Exception) {
            log.warn("Failed to close LiveKit room for callId={}: {}", msg.callId, e.message)
        }

        // Forward call.end to the other party
        if (otherParty != null) {
            val json = wsJson.encodeToString<WsMessage>(msg)
            sessionManager.sendToUser(otherParty, json)
        }

        log.info("Call ended: callId={}, userId={}, reason={}", msg.callId, userId, msg.reason)
    }

    // ─── Messaging Helpers ────────────────────────────────────

    /**
     * Parses an id the client supplied, and refuses the frame with a code the client can act on if
     * it is not one.
     *
     * The remaining half of #572. `UUID.fromString` throws `IllegalArgumentException`, which is not
     * a [BusinessException], so it fell to the catch-all and was answered with `INTERNAL_ERROR` —
     * the one code that means "the server broke, a retry may help". Here nothing broke and a retry
     * of the same frame will fail forever: it is a malformed request, and `VALIDATION_ERROR` is the
     * code this file already uses for a frame it cannot parse.
     *
     * [field] goes to the log rather than into the exception message. The client renders a code, not
     * server text (that is the rule #572 exists to restore), so naming the field to the user would
     * be both untranslated and useless — while whoever is debugging the client wants exactly it.
     */
    private fun parseId(raw: String, field: String): UUID =
        try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            log.warn("Malformed {} in WebSocket frame: {}", field, raw)
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }

    private fun respondSendFailed(
        session: WebSocketSession,
        msg: WsMessage.SendMessage,
        code: ErrorCode,
        message: String?
    ) {
        val ack = WsMessage.ServerAck(
            requestId = msg.requestId,
            messageId = msg.messageId,
            status = AckStatus.ERROR,
            errorCode = code.name,
            errorMessage = message
        )
        sessionManager.send(session, wsJson.encodeToString<WsMessage>(ack))
    }

    private fun sendPong(session: WebSocketSession) {
        val pong = WsMessage.Pong
        sessionManager.send(session, wsJson.encodeToString<WsMessage>(pong))
    }

    /**
     * [code] is an [ErrorCode] rather than a String on purpose. Every call site already passed a
     * real one; the single place that did not was the send-failure ack, which invented
     * `MSG_SEND_FAILED` and shipped it for months (#572). A String parameter is what allowed that,
     * so the type is the guard.
     */
    private fun sendError(session: WebSocketSession, code: ErrorCode, message: String) {
        val error = WsMessage.Error(code = code.name, message = message)
        if (session.isOpen) {
            sessionManager.send(session, wsJson.encodeToString<WsMessage>(error))
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

        // Single query to get all unique user IDs across all conversations (replaces N+1 pattern)
        val contactUserIds = conversationRepository.findAllContactUserIds(userId)

        // Someone who blocked you is still a "contact" — you share a conversation — so without this
        // they keep receiving your live online/offline stream and the last-seen stamp that rides
        // the OFFLINE transition. Hiding presence on the profile screen alone would have been
        // cosmetic: the chat list they already have renders exactly this feed.
        val blockedBy = blockPolicy.findBlockedBy(userId, contactUserIds)
        contactUserIds.filterNot { it in blockedBy }.forEach { contactId ->
            if (sessionManager.isOnline(contactId)) {
                sessionManager.sendToUser(contactId, json)
            }
        }
    }

    private fun extractToken(session: WebSocketSession): String? {
        val uri = session.uri ?: return null
        val params = UriComponentsBuilder.fromUri(uri).build().queryParams
        return params.getFirst("token")
    }
}

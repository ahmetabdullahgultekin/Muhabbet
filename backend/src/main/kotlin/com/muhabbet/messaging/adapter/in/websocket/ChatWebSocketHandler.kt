package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.port.`in`.RecordLastSeenUseCase
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
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
    private val recordLastSeenUseCase: RecordLastSeenUseCase,
    private val callFrameHandler: CallFrameHandler,
    private val webSocketRateLimiter: WebSocketRateLimiter,
    private val presenceVisibility: PresenceVisibility
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val token = extractToken(session)
        if (token == null) {
            sessionManager.sendError(session, ErrorCode.AUTH_TOKEN_INVALID, "Missing token query parameter")
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }

        val claims = jwtProvider.validateToken(token)
        if (claims == null) {
            sessionManager.sendError(session, ErrorCode.AUTH_TOKEN_INVALID, "Invalid or expired JWT")
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
            sessionManager.refuseRateLimited(session, message.payload)
            return
        }

        val wsMessage = try {
            wsJson.decodeFromString<WsMessage>(message.payload)
        } catch (e: Exception) {
            sessionManager.sendError(session, ErrorCode.VALIDATION_ERROR, "Invalid message format: ${e.message}")
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
                is WsMessage.CallInitiate -> callFrameHandler.handleInitiate(session, userId, wsMessage)
                is WsMessage.CallAnswer -> callFrameHandler.handleAnswer(session, userId, wsMessage)
                is WsMessage.CallIceCandidate -> callFrameHandler.handleIce(userId, wsMessage)
                is WsMessage.CallEnd -> callFrameHandler.handleEnd(userId, wsMessage)
                else -> sessionManager.sendError(session, ErrorCode.VALIDATION_ERROR, "Unexpected message type from client")
            }
        } catch (e: BusinessException) {
            sessionManager.sendError(session, e.errorCode, e.message)
            log.warn("Frame rejected: type={}, {} - {}", wsMessage::class.simpleName, e.errorCode, e.message)
        } catch (e: Exception) {
            sessionManager.sendError(session, ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage)
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
                    // A malformed id is treated as absent rather than rejected: this is a hint
                    // about a blob, not a request that can fail, and the worst it costs is a
                    // view-once photo that cannot be destroyed. Whether the id names something
                    // this sender actually uploaded is settled at burn time, next to the delete
                    // (#541).
                    mediaId = msg.mediaId?.let { try { UUID.fromString(it) } catch (_: Exception) { null } },
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
            sessionManager.sendFailedAck(session, msg, e.errorCode, e.message)
            log.warn("Message rejected: {} - {}", e.errorCode, e.message)
        } catch (e: Exception) {
            // A genuine fault, and kept distinct from a refusal on both channels: the client is told
            // INTERNAL_ERROR rather than something from the message domain, and the log carries the
            // stack trace instead of just `e.message`. GlobalExceptionHandler draws the same line
            // for the same reason — so this line stays worth paging on.
            sessionManager.sendFailedAck(session, msg, ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage)
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

        val hidden = presenceVisibility.hiddenFromTypingIn(conversationId, userId, recipientIds)
        recipientIds.forEach { recipientId ->
            if (recipientId !in hidden && sessionManager.isOnline(recipientId)) {
                sessionManager.sendToUser(recipientId, json)
            }
        }
    }

    // ─── Messaging Helpers ────────────────────────────────────

    private fun sendPong(session: WebSocketSession) {
        val pong = WsMessage.Pong
        sessionManager.send(session, wsJson.encodeToString<WsMessage>(pong))
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
        //
        // Both directions (#711), and this frame is why the REST guard alone was not enough. The
        // filter here used to hide the *broadcaster* from whoever had blocked them, which is the
        // exact opposite of what `ConversationController` withheld — so each channel closed the
        // direction the other left open, and the pair leaked both ways. The blocked person opened
        // the chat list to no dot, then watched it light up seconds later when the blocker's socket
        // connected, because `ConversationListScreen` writes this frame straight into its state.
        val hidden = presenceVisibility.hiddenFromPresenceOf(userId, contactUserIds)
        contactUserIds.filterNot { it in hidden }.forEach { contactId ->
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

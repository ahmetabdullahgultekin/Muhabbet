package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.CallStatus
import com.muhabbet.messaging.domain.port.out.CallRoomProvider
import com.muhabbet.messaging.domain.service.CallBusyException
import com.muhabbet.messaging.domain.service.CallSignalingService
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import java.util.UUID

/**
 * The call half of the WebSocket protocol: `call.initiate`, `call.answer`, `call.ice`, `call.end`.
 *
 * These four were the only reason [ChatWebSocketHandler] held [CallSignalingService],
 * [CallRoomProvider] and [UserRepository] — three dependencies nothing else in that file touched.
 * Adding one more for #402 took its constructor to twelve parameters and detekt's
 * `LongParameterList` refused it, which is the symptom rather than the problem: a chat socket and a
 * call socket change for different reasons, and CLAUDE.md asks a class to have one. The parameter
 * count came down from twelve to nine because the concern moved, not because anything was hidden.
 *
 * This is a frame router, not a service. The rules about what a call may do live in
 * [CallSignalingService]; what belongs here is the translation between wire frames and that service,
 * and the fan-out to the other party. Nothing here was rewritten in the move — see #367-#373 for
 * what is actually broken about calls end to end, none of which this touches.
 */
@Component
class CallFrameHandler(
    private val sessionManager: WebSocketSessionManager,
    private val callSignalingService: CallSignalingService,
    private val callRoomProvider: CallRoomProvider,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handleInitiate(session: WebSocketSession, callerId: UUID, msg: WsMessage.CallInitiate) {
        val calleeId = try {
            UUID.fromString(msg.targetUserId)
        } catch (e: Exception) {
            sessionManager.sendError(session, ErrorCode.CALL_INVALID_TARGET, "Invalid target user ID")
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

    fun handleAnswer(session: WebSocketSession, userId: UUID, msg: WsMessage.CallAnswer) {
        val callSession = callSignalingService.getCall(msg.callId)
        if (callSession == null) {
            sessionManager.sendError(session, ErrorCode.CALL_NOT_FOUND, "Call ${msg.callId} not found")
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

    fun handleIce(userId: UUID, msg: WsMessage.CallIceCandidate) {
        val otherParty = callSignalingService.getOtherParty(msg.callId, userId) ?: return

        // Forward ICE candidate to the other party
        val json = wsJson.encodeToString<WsMessage>(msg)
        sessionManager.sendToUser(otherParty, json)
    }

    fun handleEnd(userId: UUID, msg: WsMessage.CallEnd) {
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
}

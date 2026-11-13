package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.CallSession
import com.muhabbet.messaging.domain.model.CallState
import com.muhabbet.messaging.domain.model.CallType
import com.muhabbet.messaging.domain.port.`in`.CallPlacement
import com.muhabbet.messaging.domain.port.`in`.ConductCallUseCase
import com.muhabbet.messaging.domain.port.`in`.PlaceCallUseCase
import com.muhabbet.messaging.domain.port.out.CallRoomProvider
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.CallEndReason
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
 * This is a frame router, not a service. The rules about what a call may do, and who may do it,
 * live in [com.muhabbet.messaging.domain.model.CallSession] behind [PlaceCallUseCase] and
 * [ConductCallUseCase] (#878); what belongs here is the translation between wire frames and those
 * use cases, and the fan-out to the other party. Every frame from someone who is not in the call is
 * answered as if the call did not exist, and nothing is forwarded on their behalf.
 */
@Component
class CallFrameHandler(
    private val sessionManager: WebSocketSessionManager,
    private val placeCall: PlaceCallUseCase,
    private val conductCall: ConductCallUseCase,
    private val callRoomProvider: CallRoomProvider,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handleInitiate(session: WebSocketSession, callerId: UUID, msg: WsMessage.CallInitiate) {
        val calleeId = try {
            UUID.fromString(msg.targetUserId)
        } catch (e: IllegalArgumentException) {
            sessionManager.sendError(session, ErrorCode.CALL_INVALID_TARGET, "Invalid target user ID")
            return
        }

        val placement = guarded(session) {
            placeCall.placeCall(msg.callId, callerId, calleeId, CallType.valueOf(msg.callType.name))
        } ?: return

        val ringing = when (placement) {
            CallPlacement.Busy -> return send(session, WsMessage.CallEnd(callId = msg.callId, reason = CallEndReason.BUSY))
            // Looks exactly like a call nobody answered, which is what the caller should believe.
            CallPlacement.Unreachable -> return send(session, WsMessage.CallEnd(callId = msg.callId, reason = CallEndReason.MISSED))
            is CallPlacement.Ringing -> placement.session
        }

        if (!sessionManager.isOnline(calleeId)) {
            // The caller giving up on a ringing call is what an offline callee amounts to: MISSED.
            guarded(session) { conductCall.hangUp(msg.callId, callerId) }
            send(session, WsMessage.CallEnd(callId = msg.callId, reason = CallEndReason.MISSED))
            return
        }

        val incoming = WsMessage.CallIncoming(
            callId = ringing.callId,
            callerId = callerId.toString(),
            callerName = userRepository.findById(callerId)?.displayName,
            callType = msg.callType
        )
        sessionManager.sendToUser(calleeId, wsJson.encodeToString<WsMessage>(incoming))

        // Also forward the SDP offer if present (caller's offer → callee)
        if (msg.sdpOffer != null) {
            sessionManager.sendToUser(calleeId, wsJson.encodeToString<WsMessage>(msg))
        }

        log.info("Call initiated: callId={}, caller={}, callee={}", msg.callId, callerId, calleeId)
    }

    fun handleAnswer(session: WebSocketSession, userId: UUID, msg: WsMessage.CallAnswer) {
        val call = guarded(session) { conductCall.respond(msg.callId, userId, msg.accepted) } ?: return
        val otherParty = call.otherParty(userId) ?: return

        if (call.state is CallState.Active) openRoom(session, call)

        // Forward the answer to the other party
        sessionManager.sendToUser(otherParty, wsJson.encodeToString<WsMessage>(msg))
        log.info("Call answer: callId={}, userId={}, accepted={}", msg.callId, userId, msg.accepted)
    }

    fun handleIce(userId: UUID, msg: WsMessage.CallIceCandidate) {
        // A non-participant has no other party, so their candidates go nowhere.
        val otherParty = conductCall.otherParty(msg.callId, userId) ?: return
        sessionManager.sendToUser(otherParty, wsJson.encodeToString<WsMessage>(msg))
    }

    fun handleEnd(session: WebSocketSession, userId: UUID, msg: WsMessage.CallEnd) {
        // The outcome is the domain's to decide from the call's state, not the client's to assert.
        val ended = guarded(session) { conductCall.hangUp(msg.callId, userId) } ?: return
        closeRoom(ended.callId)
        ended.otherParty(userId)?.let { send(it, msg) }
        log.info("Call ended: callId={}, userId={}, state={}", msg.callId, userId, ended.state)
    }

    /** Tells both parties of a call the ring timeout ended that it was missed. */
    fun announceMissed(call: CallSession) {
        val missed = WsMessage.CallEnd(callId = call.callId, reason = CallEndReason.MISSED)
        call.participants.forEach { send(it, missed) }
    }

    private fun openRoom(session: WebSocketSession, call: CallSession) {
        try {
            val room = callRoomProvider.createRoom(call.callId, call.callerId, call.calleeId)
            if (room.serverUrl.isBlank()) return
            listOf(call.callerId, call.calleeId).forEach { participant ->
                val name = userRepository.findById(participant)?.displayName
                val info = WsMessage.CallRoomInfo(
                    callId = call.callId,
                    serverUrl = room.serverUrl,
                    token = callRoomProvider.generateParticipantToken(room.roomName, participant, name),
                    roomName = room.roomName
                )
                if (participant == call.calleeId) send(session, info) else send(participant, info)
            }
            log.info("LiveKit room created: callId={}, room={}", call.callId, room.roomName)
        } catch (e: Exception) {
            log.warn("Failed to create LiveKit room for callId={}: {}", call.callId, e.message)
        }
    }

    private fun closeRoom(callId: String) {
        try {
            callRoomProvider.closeRoom("call-$callId")
        } catch (e: Exception) {
            log.warn("Failed to close LiveKit room for callId={}: {}", callId, e.message)
        }
    }

    /** Runs [block]; a refusal goes back to the sender as an error frame and yields null. */
    private fun <T> guarded(session: WebSocketSession, block: () -> T): T? = try {
        block()
    } catch (e: BusinessException) {
        sessionManager.sendError(session, e.errorCode, e.errorCode.defaultMessage)
        null
    }

    private fun send(session: WebSocketSession, message: WsMessage) {
        sessionManager.send(session, wsJson.encodeToString<WsMessage>(message))
    }

    private fun send(userId: UUID, message: WsMessage) {
        sessionManager.sendToUser(userId, wsJson.encodeToString<WsMessage>(message))
    }
}

package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.AckStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.web.socket.WebSocketSession
import java.util.UUID

/**
 * The two things every WebSocket frame handler needs, kept in one place so there is one of each.
 *
 * Both lived as private members of [ChatWebSocketHandler] while it was the only frame handler.
 * Splitting the call frames into [CallFrameHandler] would have meant a second copy of `sendError`,
 * and a second copy is how the two would eventually come to answer differently.
 */
private val log = LoggerFactory.getLogger("com.muhabbet.messaging.adapter.in.websocket.WsFrameSupport")

/**
 * Sends a protocol-level error frame, if the socket is still open.
 *
 * [code] is an [ErrorCode] rather than a String on purpose. Every call site already passed a real
 * one; the single place that did not was the send-failure ack, which invented `MSG_SEND_FAILED` and
 * shipped it for months (#572). A String parameter is what allowed that, so the type is the guard.
 */
internal fun WebSocketSessionManager.sendError(session: WebSocketSession, code: ErrorCode, message: String) {
    val error = WsMessage.Error(code = code.name, message = message)
    if (session.isOpen) {
        send(session, wsJson.encodeToString<WsMessage>(error))
    }
}

/**
 * Refuses a `message.send` on the ack the sender is waiting for.
 *
 * The counterpart of [sendError] for the one frame type that has a reply of its own. Both take an
 * [ErrorCode] for the same reason — the string this used to build by hand is how `MSG_SEND_FAILED`,
 * which is not a member of the enum, reached clients for months (#572).
 */
internal fun WebSocketSessionManager.sendFailedAck(
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
    send(session, wsJson.encodeToString<WsMessage>(ack))
}

/**
 * Answers a frame the rate limiter refused — with an ack, when the frame was a send (#725).
 *
 * A refusal used to leave the socket with a bare [WsMessage.Error]. The sender's bubble is settled
 * by the `ServerAck` correlated to its `requestId` and by nothing else, so a refused send got no
 * answer it could act on: the clock icon it was given on send stayed there forever,
 * indistinguishable from a slow network, and the natural response is to send it again — which is
 * the one thing the limiter exists to prevent.
 *
 * The alternative was to teach the chat screen to read `Error` frames and correlate them itself. It
 * cannot: an `Error` carries a code and a sentence, no `requestId` and no `messageId`, so there is
 * nothing to attach it to and no way to tell which of several in-flight sends it refers to. Widening
 * that frame would have invented a second, parallel way to fail a send alongside the one #572 had
 * just finished making coherent. Answering on the ack instead means every `message.send` gets
 * exactly one reply, always of the same type, and `RATE_LIMITED` lands in the client's existing
 * code→sentence mapping with no new client concept at all.
 *
 * Parsing here costs one decode of a frame that is about to be discarded, and only for frames over
 * the limit — an accepted frame is still decoded exactly once, by the caller. The error frame this
 * writes back costs more than the parse that addresses it.
 *
 * Anything that is not a send keeps the bare error: a typing indicator or an ack has no `requestId`
 * to answer on, and nothing on the client is waiting for a reply to it.
 */
internal fun WebSocketSessionManager.refuseRateLimited(session: WebSocketSession, payload: String) {
    val send = try {
        wsJson.decodeFromString<WsMessage>(payload) as? WsMessage.SendMessage
    } catch (e: Exception) {
        log.debug("Rate-limited frame could not be parsed: {}", e.message)
        null
    }
    if (send != null) {
        sendFailedAck(session, send, ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.defaultMessage)
    } else {
        sendError(session, ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.defaultMessage)
    }
}

/**
 * Parses an id the client supplied, and refuses the frame with a code the client can act on if it is
 * not one.
 *
 * The remaining half of #572. `UUID.fromString` throws `IllegalArgumentException`, which is not a
 * [BusinessException], so it fell to the catch-all and was answered with `INTERNAL_ERROR` — the one
 * code that means "the server broke, a retry may help". Here nothing broke and a retry of the same
 * frame will fail forever: it is a malformed request, and `VALIDATION_ERROR` is the code this
 * package already uses for a frame it cannot parse.
 *
 * [field] goes to the log rather than into the exception message. The client renders a code, not
 * server text (that is the rule #572 exists to restore), so naming the field to the user would be
 * both untranslated and useless — while whoever is debugging the client wants exactly it.
 */
internal fun parseId(raw: String, field: String): UUID =
    try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        log.warn("Malformed {} in WebSocket frame: {}", field, raw)
        throw BusinessException(ErrorCode.VALIDATION_ERROR)
    }

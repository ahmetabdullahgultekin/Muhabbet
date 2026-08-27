package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
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

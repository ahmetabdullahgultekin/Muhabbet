package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.port.`in`.RecordLastSeenUseCase
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.security.WebSocketRateLimiter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.UUID

/**
 * #572 — the WebSocket path must answer with the code the domain actually produced.
 *
 * `handleSendMessage` caught every exception and replied `MSG_SEND_FAILED`, a string that is not an
 * `ErrorCode` and never was. "Too long", "not a member", "announcement only", "already sent" and a
 * genuine server fault all reached the client as the same code, so the client could not map a code
 * to a localized string — the project's own rule — and fell back to a generic sentence or to the
 * server's Turkish text shown to someone reading the app in English. The REST path never had this
 * problem: `GlobalExceptionHandler` answers with `errorCode.name`. These tests hold the socket to
 * the same contract.
 */
class WebSocketErrorContractTest {

    private val sendMessageUseCase: SendMessageUseCase = mockk()
    private val sessionManager: WebSocketSessionManager = mockk(relaxed = true)
    private val jwtProvider: JwtProvider = mockk(relaxed = true)
    private val rateLimiter: WebSocketRateLimiter = mockk()
    private val session: WebSocketSession = mockk(relaxed = true)

    private val userId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()

    private val handler = ChatWebSocketHandler(
        jwtProvider = jwtProvider,
        sessionManager = sessionManager,
        sendMessageUseCase = sendMessageUseCase,
        updateDeliveryStatusUseCase = mockk<UpdateDeliveryStatusUseCase>(relaxed = true),
        conversationRepository = mockk<ConversationRepository>(relaxed = true),
        presencePort = mockk<PresencePort>(relaxed = true),
        recordLastSeenUseCase = mockk<RecordLastSeenUseCase>(relaxed = true),
        callFrameHandler = mockk<CallFrameHandler>(relaxed = true),
        webSocketRateLimiter = rateLimiter,
        presenceVisibility = PresenceVisibility(
            mockk<BlockPolicyPort>(relaxed = true),
            mockk<ConversationRepository>(relaxed = true)
        )
    )

    @BeforeEach
    fun setUp() {
        every { rateLimiter.allowMessage(any()) } returns true
        every { session.attributes } returns mutableMapOf<String, Any>("userId" to userId)
        every { session.isOpen } returns true
        every { session.id } returns "s-1"
    }

    private fun send(frame: WsMessage): List<String> {
        val sent = mutableListOf<String>()
        every { sessionManager.send(session, capture(sent)) } returns Unit
        handler.handleMessage(session, TextMessage(wsJson.encodeToString<WsMessage>(frame)))
        return sent
    }

    private fun sendMessageFrame() = WsMessage.SendMessage(
        requestId = "req-1",
        messageId = UUID.randomUUID().toString(),
        conversationId = conversationId.toString(),
        content = "merhaba"
    )

    private fun ackOf(json: String) = wsJson.decodeFromString<WsMessage>(json) as WsMessage.ServerAck

    @Test
    fun `should answer with the real error code when the domain refuses the send`() {
        every { sendMessageUseCase.sendMessage(any()) } throws BusinessException(ErrorCode.MSG_NOT_MEMBER)

        val ack = ackOf(send(sendMessageFrame()).single())

        assertEquals(ErrorCode.MSG_NOT_MEMBER.name, ack.errorCode)
        assertEquals(ErrorCode.MSG_NOT_MEMBER.defaultMessage, ack.errorMessage)
    }

    @Test
    fun `should distinguish a message that is too long from one whose sender is not a member`() {
        // The distinction the client needs and could not previously make: both used to arrive as
        // MSG_SEND_FAILED.
        every { sendMessageUseCase.sendMessage(any()) } throws BusinessException(ErrorCode.MSG_CONTENT_TOO_LONG)
        val tooLong = ackOf(send(sendMessageFrame()).single())

        every { sendMessageUseCase.sendMessage(any()) } throws BusinessException(ErrorCode.MSG_NOT_MEMBER)
        val notMember = ackOf(send(sendMessageFrame()).single())

        assertEquals(ErrorCode.MSG_CONTENT_TOO_LONG.name, tooLong.errorCode)
        assertEquals(ErrorCode.MSG_NOT_MEMBER.name, notMember.errorCode)
    }

    @Test
    fun `should answer with INTERNAL_ERROR when the failure is not a domain refusal`() {
        every { sendMessageUseCase.sendMessage(any()) } throws RuntimeException("DB down")

        val ack = ackOf(send(sendMessageFrame()).single())

        assertEquals(ErrorCode.INTERNAL_ERROR.name, ack.errorCode)
    }

    @Test
    fun `should never answer with a code that is not an ErrorCode`() {
        // The regression itself. MSG_SEND_FAILED was invented at the call site and is not a member
        // of the enum, which is exactly how it survived.
        val codes = ErrorCode.entries.map { it.name }.toSet()

        every { sendMessageUseCase.sendMessage(any()) } throws BusinessException(ErrorCode.MSG_DUPLICATE)
        val refusal = ackOf(send(sendMessageFrame()).single())
        every { sendMessageUseCase.sendMessage(any()) } throws RuntimeException("boom")
        val fault = ackOf(send(sendMessageFrame()).single())

        assertTrue(refusal.errorCode in codes, "${refusal.errorCode} is not an ErrorCode")
        assertTrue(fault.errorCode in codes, "${fault.errorCode} is not an ErrorCode")
    }

    @Test
    fun `should keep the connection open when a frame carries a malformed conversation id`() {
        // handleTypingIndicator parses a client-supplied id with no guard of its own. Before the
        // top-level catch, the IllegalArgumentException escaped into the container and the socket
        // was closed — a typing indicator cost the user their chat.
        //
        // The code it answers with used to be INTERNAL_ERROR, because IllegalArgumentException is
        // not a BusinessException and fell to the catch-all. That is the same flattening #572 is
        // about, one layer down: nothing on the server broke, and a retry of an id that is not a
        // UUID will fail forever.
        val sent = send(
            WsMessage.TypingIndicator(conversationId = "not-a-uuid", isTyping = true)
        )

        verify(exactly = 0) { session.close(any()) }
        val error = wsJson.decodeFromString<WsMessage>(sent.single()) as WsMessage.Error
        assertEquals(ErrorCode.VALIDATION_ERROR.name, error.code)
    }

    @Test
    fun `should answer a malformed message id with a validation code and not attempt the send`() {
        // The send path's own version of the same thing, and the one that reaches the sender as an
        // ack rather than a bare error: a malformed id must be distinguishable from "the database
        // is down", because only one of the two is worth retrying.
        val sent = send(sendMessageFrame().copy(messageId = "not-a-uuid"))

        val ack = ackOf(sent.single())
        assertEquals(ErrorCode.VALIDATION_ERROR.name, ack.errorCode)
        verify(exactly = 0) { sendMessageUseCase.sendMessage(any()) }
    }
}

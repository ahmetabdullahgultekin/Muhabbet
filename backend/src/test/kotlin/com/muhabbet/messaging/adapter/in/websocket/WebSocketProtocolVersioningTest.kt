package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.port.`in`.RecordLastSeenUseCase
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.ChatActivityKind
import com.muhabbet.shared.model.PresenceStatus
import com.muhabbet.shared.protocol.AckStatus
import com.muhabbet.shared.protocol.WireProtocol
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.JwtClaims
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.security.WebSocketRateLimiter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.net.URI
import java.util.UUID

/**
 * Version skew between an installed app and this server (#873), and the typing frame that depends on
 * knowing which side of it a client is on (#885).
 */
class WebSocketProtocolVersioningTest {

    private val sessionManager: WebSocketSessionManager = mockk(relaxed = true)
    private val jwtProvider: JwtProvider = mockk(relaxed = true)
    private val rateLimiter: WebSocketRateLimiter = mockk()
    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val sendMessageUseCase: SendMessageUseCase = mockk(relaxed = true)

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()

    private fun handler(minSupported: Int = WireProtocol.LEGACY_VERSION) = ChatWebSocketHandler(
        jwtProvider = jwtProvider,
        sessionManager = sessionManager,
        sendMessageUseCase = sendMessageUseCase,
        updateDeliveryStatusUseCase = mockk<UpdateDeliveryStatusUseCase>(relaxed = true),
        conversationRepository = conversationRepository,
        presencePort = mockk<PresencePort>(relaxed = true),
        recordLastSeenUseCase = mockk<RecordLastSeenUseCase>(relaxed = true),
        callFrameHandler = mockk<CallFrameHandler>(relaxed = true),
        webSocketRateLimiter = rateLimiter,
        presenceVisibility = PresenceVisibility(
            mockk<BlockPolicyPort>(relaxed = true),
            mockk<ConversationRepository>(relaxed = true)
        ),
        minSupportedProtocol = minSupported
    )

    private fun session(query: String, attributes: MutableMap<String, Any> = mutableMapOf()): WebSocketSession {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.uri } returns URI("wss://localhost/ws?$query")
        every { session.id } returns UUID.randomUUID().toString()
        every { session.isOpen } returns true
        every { session.attributes } returns attributes
        return session
    }

    @BeforeEach
    fun setUp() {
        every { rateLimiter.allowMessage(any()) } returns true
    }

    private fun errorFramesSentTo(session: WebSocketSession): List<WsMessage.Error> {
        val sent = mutableListOf<String>()
        verify(atLeast = 0) { sessionManager.send(session, capture(sent)) }
        return sent.map { wsJson.decodeFromString<WsMessage>(it) }.filterIsInstance<WsMessage.Error>()
    }

    // ─── Minimum protocol ───────────────────────────────

    @Test
    fun `should close with 4426 and CLIENT_UPGRADE_REQUIRED when the client is below the minimum`() {
        val session = session("token=t&pv=0")

        handler(minSupported = 1).afterConnectionEstablished(session)

        verify { session.close(CloseStatus(WireProtocol.CLOSE_UPGRADE_REQUIRED)) }
        assertEquals(listOf(ErrorCode.CLIENT_UPGRADE_REQUIRED), errorFramesSentTo(session).map { it.code })
        verify(exactly = 0) { jwtProvider.validateToken(any()) }
        verify(exactly = 0) { sessionManager.register(any(), any()) }
    }

    @Test
    fun `should treat a client that announces nothing as the legacy protocol`() {
        val session = session("token=t")

        handler(minSupported = 1).afterConnectionEstablished(session)

        verify { session.close(CloseStatus(WireProtocol.CLOSE_UPGRADE_REQUIRED)) }
    }

    @Test
    fun `should accept and record the protocol of a client at or above the minimum`() {
        val attributes = mutableMapOf<String, Any>()
        val session = session("token=t&pv=1&app=0.5.0&platform=android", attributes)
        every { jwtProvider.validateToken("t") } returns JwtClaims(userId = userId, deviceId = UUID.randomUUID())

        handler(minSupported = 1).afterConnectionEstablished(session)

        verify(exactly = 0) { session.close(any()) }
        assertEquals(1, attributes[WebSocketSessionManager.PROTOCOL_VERSION_ATTRIBUTE])
    }

    // ─── No parser text on the wire ─────────────────────

    @Test
    fun `should answer a malformed frame with a code and no parser text`() {
        val session = session("token=t", mutableMapOf("userId" to userId))

        handler().handleMessage(session, TextMessage("""{"type":"message.send","requestId":"""))

        val error = errorFramesSentTo(session).single()
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code)
        assertFalse(error.message.contains("JSON", ignoreCase = true), error.message)
        assertFalse(error.message.contains("offset", ignoreCase = true), error.message)
        assertFalse(error.message.contains("Invalid message format"), error.message)
    }

    @Test
    fun `should answer a frame type this server does not know with VALIDATION_ERROR`() {
        val session = session("token=t", mutableMapOf("userId" to userId))

        handler().handleMessage(session, TextMessage("""{"type":"future.frame","x":1}"""))

        assertEquals(listOf(ErrorCode.VALIDATION_ERROR), errorFramesSentTo(session).map { it.code })
    }

    @Test
    fun `should refuse a message whose content type this server does not know instead of storing TEXT`() {
        val session = session("token=t", mutableMapOf("userId" to userId))
        val frame = """{"type":"message.send","requestId":"r1","messageId":"${UUID.randomUUID()}",
            "conversationId":"$conversationId","content":"x","contentType":"HOLOGRAM"}"""
        val sent = slot<String>()
        every { sessionManager.send(session, capture(sent)) } returns Unit

        handler().handleMessage(session, TextMessage(frame))

        val ack = wsJson.decodeFromString<WsMessage>(sent.captured) as WsMessage.ServerAck
        assertEquals(AckStatus.ERROR, ack.status)
        assertEquals(ErrorCode.VALIDATION_ERROR, ack.errorCode)
        verify(exactly = 0) { sendMessageUseCase.sendMessage(any()) }
    }

    // ─── Typing is chat activity, not presence (#885) ───

    private fun typingPayloadFor(protocol: Int, isTyping: Boolean): WsMessage {
        every { conversationRepository.findMembersByConversationId(conversationId) } returns listOf(
            ConversationMember(conversationId = conversationId, userId = userId),
            ConversationMember(conversationId = conversationId, userId = otherUserId)
        )
        every { sessionManager.isOnline(otherUserId) } returns true
        val chooser = slot<(Int) -> String>()
        every { sessionManager.sendToUserByProtocol(otherUserId, capture(chooser)) } returns Unit

        val session = session("token=t", mutableMapOf("userId" to userId))
        val typing = WsMessage.TypingIndicator(conversationId = conversationId.toString(), isTyping = isTyping)
        handler().handleMessage(session, TextMessage(wsJson.encodeToString<WsMessage>(typing)))

        return wsJson.decodeFromString<WsMessage>(chooser.captured(protocol))
    }

    @Test
    fun `should send stopped typing as chat activity IDLE to a protocol 1 client, not as a presence change`() {
        val frame = typingPayloadFor(protocol = 1, isTyping = false)

        assertTrue(frame is WsMessage.ChatActivity, "$frame")
        frame as WsMessage.ChatActivity
        assertEquals(ChatActivityKind.IDLE, frame.activity)
        assertEquals(userId.toString(), frame.userId)
        assertEquals(conversationId.toString(), frame.conversationId)
    }

    @Test
    fun `should send typing as chat activity TYPING to a protocol 1 client`() {
        val frame = typingPayloadFor(protocol = 1, isTyping = true) as WsMessage.ChatActivity

        assertEquals(ChatActivityKind.TYPING, frame.activity)
    }

    @Test
    fun `should keep sending a legacy client the presence form it understands`() {
        val stopped = typingPayloadFor(protocol = 0, isTyping = false) as WsMessage.PresenceUpdate
        assertEquals(PresenceStatus.ONLINE, stopped.status)
        assertEquals(conversationId.toString(), stopped.conversationId)

        val typing = typingPayloadFor(protocol = 0, isTyping = true) as WsMessage.PresenceUpdate
        assertEquals(PresenceStatus.TYPING, typing.status)
    }
}

package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.JwtClaims
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.net.URI
import java.time.Instant
import java.util.UUID

class ChatWebSocketHandlerTest {

    private lateinit var jwtProvider: JwtProvider
    private lateinit var sessionManager: WebSocketSessionManager
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var updateDeliveryStatusUseCase: UpdateDeliveryStatusUseCase
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var presencePort: PresencePort
    private lateinit var userRepository: UserRepository
    private lateinit var handler: ChatWebSocketHandler

    private val userId = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val jwtProperties = JwtProperties(
            secret = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256",
            accessTokenExpiry = 900,
            refreshTokenExpiry = 2592000,
            issuer = "muhabbet-test"
        )
        jwtProvider = JwtProvider(jwtProperties)

        sessionManager = mockk(relaxed = true)
        sendMessageUseCase = mockk(relaxed = true)
        updateDeliveryStatusUseCase = mockk(relaxed = true)
        conversationRepository = mockk(relaxed = true)
        presencePort = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)

        handler = ChatWebSocketHandler(
            jwtProvider = jwtProvider,
            sessionManager = sessionManager,
            sendMessageUseCase = sendMessageUseCase,
            updateDeliveryStatusUseCase = updateDeliveryStatusUseCase,
            conversationRepository = conversationRepository,
            presencePort = presencePort,
            userRepository = userRepository
        )
    }

    private fun createSession(token: String? = null): WebSocketSession {
        val session = mockk<WebSocketSession>(relaxed = true)
        val uri = if (token != null) {
            URI("wss://localhost/ws?token=$token")
        } else {
            URI("wss://localhost/ws")
        }
        every { session.uri } returns uri
        every { session.id } returns UUID.randomUUID().toString()
        every { session.isOpen } returns true
        every { session.attributes } returns mutableMapOf<String, Any>()
        return session
    }

    private fun generateValidToken(): String {
        return jwtProvider.generateAccessToken(userId, deviceId)
    }

    // ─── Connection / JWT Validation ──────────────────────

    @Nested
    inner class ConnectionEstablished {

        @Test
        fun `should register session when token is valid`() {
            val token = generateValidToken()
            val session = createSession(token)

            handler.afterConnectionEstablished(session)

            verify { sessionManager.register(userId, session) }
            verify { presencePort.setOnline(userId) }
        }

        @Test
        fun `should store userId in session attributes when token is valid`() {
            val token = generateValidToken()
            val attrs = mutableMapOf<String, Any>()
            val session = createSession(token)
            every { session.attributes } returns attrs

            handler.afterConnectionEstablished(session)

            assertTrue(attrs.containsKey("userId"))
            assertTrue(attrs.containsKey("deviceId"))
        }

        @Test
        fun `should close session with error when token is missing`() {
            val session = createSession(token = null)

            handler.afterConnectionEstablished(session)

            verify { session.close(CloseStatus.POLICY_VIOLATION) }
            verify(exactly = 0) { sessionManager.register(any(), any()) }
        }

        @Test
        fun `should close session with error when token is invalid`() {
            val session = createSession(token = "invalid-jwt-token")

            handler.afterConnectionEstablished(session)

            verify { session.close(CloseStatus.POLICY_VIOLATION) }
            verify(exactly = 0) { sessionManager.register(any(), any()) }
        }

        @Test
        fun `should close session when token is expired`() {
            // Create a provider with 0 second expiry to get an expired token
            val expiredProps = JwtProperties(
                secret = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256",
                accessTokenExpiry = 0,
                refreshTokenExpiry = 2592000,
                issuer = "muhabbet-test"
            )
            val expiredProvider = JwtProvider(expiredProps)
            val expiredToken = expiredProvider.generateAccessToken(userId, deviceId)

            // Small delay to ensure token is expired
            Thread.sleep(10)

            val session = createSession(token = expiredToken)

            handler.afterConnectionEstablished(session)

            verify { session.close(CloseStatus.POLICY_VIOLATION) }
            verify(exactly = 0) { sessionManager.register(any(), any()) }
        }

        @Test
        fun `should send error message before closing on invalid token`() {
            val session = createSession(token = "bad-token")
            val messageSlot = slot<TextMessage>()
            every { session.sendMessage(capture(messageSlot)) } just Runs

            handler.afterConnectionEstablished(session)

            val sentJson = messageSlot.captured.payload
            assertTrue(sentJson.contains("AUTH_TOKEN_INVALID"))
        }
    }

    // ─── Message Handling ──────────────────────────────────

    @Nested
    inner class HandleTextMessage {

        @Test
        fun `should send ServerAck OK when message is sent successfully`() {
            val token = generateValidToken()
            val session = createSession(token)
            val attrs = mutableMapOf<String, Any>("userId" to userId, "deviceId" to deviceId)
            every { session.attributes } returns attrs

            val convId = UUID.randomUUID()
            val messageId = UUID.randomUUID()
            val now = Instant.now()

            val sendMessage = WsMessage.SendMessage(
                requestId = "req-1",
                messageId = messageId.toString(),
                conversationId = convId.toString(),
                content = "Hello!"
            )

            every { sendMessageUseCase.sendMessage(any()) } returns Message(
                id = messageId,
                conversationId = convId,
                senderId = userId,
                content = "Hello!",
                serverTimestamp = now,
                clientTimestamp = now
            )

            val json = wsJson.encodeToString<WsMessage>(sendMessage)
            handler.handleTextMessage(session, TextMessage(json))

            val messageSlot = slot<TextMessage>()
            verify { session.sendMessage(capture(messageSlot)) }

            val ackJson = messageSlot.captured.payload
            assertTrue(ackJson.contains("\"status\":\"OK\""))
            assertTrue(ackJson.contains("req-1"))
        }

        @Test
        fun `should send ServerAck ERROR when message sending fails`() {
            val session = createSession()
            val attrs = mutableMapOf<String, Any>("userId" to userId, "deviceId" to deviceId)
            every { session.attributes } returns attrs

            val sendMessage = WsMessage.SendMessage(
                requestId = "req-2",
                messageId = UUID.randomUUID().toString(),
                conversationId = UUID.randomUUID().toString(),
                content = "Hello!"
            )

            every { sendMessageUseCase.sendMessage(any()) } throws RuntimeException("DB down")

            val json = wsJson.encodeToString<WsMessage>(sendMessage)
            handler.handleTextMessage(session, TextMessage(json))

            val messageSlot = slot<TextMessage>()
            verify { session.sendMessage(capture(messageSlot)) }

            val ackJson = messageSlot.captured.payload
            assertTrue(ackJson.contains("\"status\":\"ERROR\""))
        }

        @Test
        fun `should send error when message format is invalid JSON`() {
            val session = createSession()
            val attrs = mutableMapOf<String, Any>("userId" to userId)
            every { session.attributes } returns attrs

            handler.handleTextMessage(session, TextMessage("{invalid json"))

            val messageSlot = slot<TextMessage>()
            verify { session.sendMessage(capture(messageSlot)) }

            val sentJson = messageSlot.captured.payload
            assertTrue(sentJson.contains("VALIDATION_ERROR"))
        }

        @Test
        fun `should ignore message when userId is not in session attributes`() {
            val session = createSession()
            val attrs = mutableMapOf<String, Any>() // No userId
            every { session.attributes } returns attrs

            val sendMessage = WsMessage.SendMessage(
                requestId = "req-3",
                messageId = UUID.randomUUID().toString(),
                conversationId = UUID.randomUUID().toString(),
                content = "Hello!"
            )

            val json = wsJson.encodeToString<WsMessage>(sendMessage)
            handler.handleTextMessage(session, TextMessage(json))

            // Should not process the message at all
            verify(exactly = 0) { sendMessageUseCase.sendMessage(any()) }
        }

        @Test
        fun `should handle ping message and respond with pong`() {
            val session = createSession()
            val attrs = mutableMapOf<String, Any>("userId" to userId)
            every { session.attributes } returns attrs

            val pingJson = wsJson.encodeToString<WsMessage>(WsMessage.Ping)
            handler.handleTextMessage(session, TextMessage(pingJson))

            val messageSlot = slot<TextMessage>()
            verify { session.sendMessage(capture(messageSlot)) }

            val sentJson = messageSlot.captured.payload
            assertTrue(sentJson.contains("pong"))
            verify { presencePort.setOnline(userId) }
        }

        @Test
        fun `should handle GoOnline message and set presence`() {
            val session = createSession()
            val attrs = mutableMapOf<String, Any>("userId" to userId)
            every { session.attributes } returns attrs

            val goOnlineJson = wsJson.encodeToString<WsMessage>(WsMessage.GoOnline)
            handler.handleTextMessage(session, TextMessage(goOnlineJson))

            verify { presencePort.setOnline(userId) }
        }

        @Test
        fun `should handle typing indicator and broadcast to conversation members`() {
            val session = createSession()
            val attrs = mutableMapOf<String, Any>("userId" to userId)
            every { session.attributes } returns attrs

            val convId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()

            every { conversationRepository.findMembersByConversationId(convId) } returns listOf(
                ConversationMember(conversationId = convId, userId = userId),
                ConversationMember(conversationId = convId, userId = otherUserId)
            )
            every { sessionManager.isOnline(otherUserId) } returns true

            val typing = WsMessage.TypingIndicator(
                conversationId = convId.toString(),
                isTyping = true
            )
            val json = wsJson.encodeToString<WsMessage>(typing)
            handler.handleTextMessage(session, TextMessage(json))

            verify { sessionManager.sendToUser(otherUserId, any()) }
            // Should NOT send to the sender
            verify(exactly = 0) { sessionManager.sendToUser(eq(userId), any()) }
        }

        @Test
        fun `should not send typing indicator to offline users`() {
            val session = createSession()
            val attrs = mutableMapOf<String, Any>("userId" to userId)
            every { session.attributes } returns attrs

            val convId = UUID.randomUUID()
            val offlineUserId = UUID.randomUUID()

            every { conversationRepository.findMembersByConversationId(convId) } returns listOf(
                ConversationMember(conversationId = convId, userId = userId),
                ConversationMember(conversationId = convId, userId = offlineUserId)
            )
            every { sessionManager.isOnline(offlineUserId) } returns false

            val typing = WsMessage.TypingIndicator(
                conversationId = convId.toString(),
                isTyping = true
            )
            val json = wsJson.encodeToString<WsMessage>(typing)
            handler.handleTextMessage(session, TextMessage(json))

            verify(exactly = 0) { sessionManager.sendToUser(offlineUserId, any()) }
        }
    }

    // ─── Connection Closed ──────────────────────────────────

    @Nested
    inner class ConnectionClosed {

        @Test
        fun `should unregister session and set offline when last session closes`() {
            val session = createSession()

            every { sessionManager.getUserId(session) } returns userId
            every { sessionManager.isOnline(userId) } returns false // No remaining sessions

            handler.afterConnectionClosed(session, CloseStatus.NORMAL)

            verify { sessionManager.unregister(session) }
            verify { presencePort.setOffline(userId) }
            verify { userRepository.updateLastSeenAt(eq(userId), any()) }
        }

        @Test
        fun `should unregister session but not set offline when other sessions remain`() {
            val session = createSession()

            every { sessionManager.getUserId(session) } returns userId
            every { sessionManager.isOnline(userId) } returns true // Other sessions still active

            handler.afterConnectionClosed(session, CloseStatus.NORMAL)

            verify { sessionManager.unregister(session) }
            verify(exactly = 0) { presencePort.setOffline(userId) }
        }

        @Test
        fun `should handle gracefully when session has no userId`() {
            val session = createSession()

            every { sessionManager.getUserId(session) } returns null

            handler.afterConnectionClosed(session, CloseStatus.NORMAL)

            verify { sessionManager.unregister(session) }
            verify(exactly = 0) { presencePort.setOffline(any()) }
        }
    }

    // ─── Transport Error ──────────────────────────────────

    @Nested
    inner class TransportError {

        @Test
        fun `should unregister session and set offline on transport error`() {
            val session = createSession()

            every { sessionManager.getUserId(session) } returns userId
            every { sessionManager.isOnline(userId) } returns false

            handler.handleTransportError(session, RuntimeException("Connection reset"))

            verify { sessionManager.unregister(session) }
            verify { presencePort.setOffline(userId) }
        }

        @Test
        fun `should not set offline on transport error when other sessions remain`() {
            val session = createSession()

            every { sessionManager.getUserId(session) } returns userId
            every { sessionManager.isOnline(userId) } returns true

            handler.handleTransportError(session, RuntimeException("Timeout"))

            verify { sessionManager.unregister(session) }
            verify(exactly = 0) { presencePort.setOffline(any()) }
        }
    }
}

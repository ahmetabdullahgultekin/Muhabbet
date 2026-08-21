package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.CallRoomProvider
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.messaging.domain.service.CallSignalingService
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.JwtClaims
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import org.springframework.mock.env.MockEnvironment
import com.muhabbet.shared.security.WebSocketRateLimiter
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
import org.springframework.web.socket.PongMessage
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
    private lateinit var callSignalingService: CallSignalingService
    private lateinit var callRoomProvider: CallRoomProvider
    private lateinit var webSocketRateLimiter: WebSocketRateLimiter
    private lateinit var blockPolicy: com.muhabbet.messaging.domain.port.out.BlockPolicyPort
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
        jwtProvider = JwtProvider(jwtProperties, MockEnvironment())

        sessionManager = mockk(relaxed = true)
        sendMessageUseCase = mockk(relaxed = true)
        updateDeliveryStatusUseCase = mockk(relaxed = true)
        conversationRepository = mockk(relaxed = true)
        presencePort = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        callSignalingService = mockk(relaxed = true)
        callRoomProvider = mockk(relaxed = true)
        webSocketRateLimiter = mockk(relaxed = true)
        blockPolicy = mockk(relaxed = true)

        // Rate limiter always allows in tests
        every { webSocketRateLimiter.allowMessage(any()) } returns true
        // Nobody has blocked anybody, so presence fans out to every contact as before.
        every { blockPolicy.findBlockedBy(any(), any()) } returns emptySet()

        handler = ChatWebSocketHandler(
            jwtProvider = jwtProvider,
            sessionManager = sessionManager,
            sendMessageUseCase = sendMessageUseCase,
            updateDeliveryStatusUseCase = updateDeliveryStatusUseCase,
            conversationRepository = conversationRepository,
            presencePort = presencePort,
            userRepository = userRepository,
            callSignalingService = callSignalingService,
            callRoomProvider = callRoomProvider,
            webSocketRateLimiter = webSocketRateLimiter,
            blockPolicy = blockPolicy
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
            val expiredProvider = JwtProvider(expiredProps, MockEnvironment())
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
            val messageSlot = slot<String>()
            every { sessionManager.send(session, capture(messageSlot)) } just Runs

            handler.afterConnectionEstablished(session)

            val sentJson = messageSlot.captured
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
            handler.handleMessage(session, TextMessage(json))

            val messageSlot = slot<String>()
            verify { sessionManager.send(session, capture(messageSlot)) }

            val ackJson = messageSlot.captured
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
            handler.handleMessage(session, TextMessage(json))

            val messageSlot = slot<String>()
            verify { sessionManager.send(session, capture(messageSlot)) }

            val ackJson = messageSlot.captured
            assertTrue(ackJson.contains("\"status\":\"ERROR\""))
        }

        @Test
        fun `should send error when message format is invalid JSON`() {
            val session = createSession()
            val attrs = mutableMapOf<String, Any>("userId" to userId)
            every { session.attributes } returns attrs

            handler.handleMessage(session, TextMessage("{invalid json"))

            val messageSlot = slot<String>()
            verify { sessionManager.send(session, capture(messageSlot)) }

            val sentJson = messageSlot.captured
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
            handler.handleMessage(session, TextMessage(json))

            // Should not process the message at all
            verify(exactly = 0) { sendMessageUseCase.sendMessage(any()) }
        }

        @Test
        fun `should handle ping message and respond with pong`() {
            val session = createSession()
            val attrs = mutableMapOf<String, Any>("userId" to userId)
            every { session.attributes } returns attrs

            val pingJson = wsJson.encodeToString<WsMessage>(WsMessage.Ping)
            handler.handleMessage(session, TextMessage(pingJson))

            val messageSlot = slot<String>()
            verify { sessionManager.send(session, capture(messageSlot)) }

            val sentJson = messageSlot.captured
            assertTrue(sentJson.contains("pong"))
            verify { presencePort.setOnline(userId) }
        }

        @Test
        fun `should record liveness for every inbound frame, including ones it rejects`() {
            // #468: liveness must not depend on the frame being well-formed or wanted — an
            // unparseable frame still proves the peer is reachable.
            val session = createSession()
            every { session.attributes } returns mutableMapOf<String, Any>("userId" to userId)

            handler.handleMessage(session, TextMessage("{invalid json"))

            verify { sessionManager.touch(session, any()) }
        }

        @Test
        fun `should record liveness when the client answers a server ping`() {
            // The only proof of life from a backgrounded client that has stopped its own heartbeat.
            val session = createSession()

            handler.handleMessage(session, PongMessage())

            verify { sessionManager.touch(session, any()) }
        }

        @Test
        fun `should handle GoOnline message and set presence`() {
            val session = createSession()
            val attrs = mutableMapOf<String, Any>("userId" to userId)
            every { session.attributes } returns attrs

            val goOnlineJson = wsJson.encodeToString<WsMessage>(WsMessage.GoOnline)
            handler.handleMessage(session, TextMessage(goOnlineJson))

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
            handler.handleMessage(session, TextMessage(json))

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
            handler.handleMessage(session, TextMessage(json))

            verify(exactly = 0) { sessionManager.sendToUser(offlineUserId, any()) }
        }
    }

    // ─── Conversation Focus (#618) ──────────────────────────

    @Nested
    inner class ConversationFocus {

        @Test
        fun `should record the reported conversation as this user's active one`() {
            val session = createSession()
            every { session.attributes } returns mutableMapOf<String, Any>("userId" to userId)
            val convId = UUID.randomUUID()

            val json = wsJson.encodeToString<WsMessage>(WsMessage.ConversationFocus(convId.toString()))
            handler.handleMessage(session, TextMessage(json))

            verify { sessionManager.setActiveConversation(userId, convId) }
        }

        @Test
        fun `should clear the active conversation when the client reports null`() {
            val session = createSession()
            every { session.attributes } returns mutableMapOf<String, Any>("userId" to userId)

            val json = wsJson.encodeToString<WsMessage>(WsMessage.ConversationFocus(conversationId = null))
            handler.handleMessage(session, TextMessage(json))

            verify { sessionManager.setActiveConversation(userId, null) }
        }

        @Test
        fun `should degrade to null rather than error on a malformed conversation id`() {
            val session = createSession()
            every { session.attributes } returns mutableMapOf<String, Any>("userId" to userId)

            val json = wsJson.encodeToString<WsMessage>(WsMessage.ConversationFocus("not-a-uuid"))
            handler.handleMessage(session, TextMessage(json))

            verify { sessionManager.setActiveConversation(userId, null) }
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

    // ─── Presence and blocks (#294, vector 2) ───────────────

    /**
     * The live half of "a blocked person must not watch you".
     *
     * `GET /users/{id}` and `GET /conversations` were both closed in #554 and both have tests. This
     * one - the push feed the chat list actually renders its green dot from - was closed in the same
     * PR and had none, so nothing stood between the guard and a future refactor. A block leaks
     * through here the moment somebody simplifies `broadcastPresence` back to "fan out to every
     * contact", and the two REST tests would still be green while it did.
     *
     * The OFFLINE transition is asserted separately from the ONLINE one because it carries strictly
     * more: `lastSeenAt` rides on it, so a guard that covered only the connect path would still hand
     * a harasser a last-seen stamp every time the victim closed the app.
     */
    @Nested
    inner class PresenceBlocks {

        private val blockerId = UUID.randomUUID()
        private val friendId = UUID.randomUUID()

        @BeforeEach
        fun stubContacts() {
            every { conversationRepository.findAllContactUserIds(userId) } returns setOf(blockerId, friendId)
            every { sessionManager.isOnline(blockerId) } returns true
            every { sessionManager.isOnline(friendId) } returns true
        }

        @Test
        fun `should not tell someone who blocked this user that they came online`() {
            every { blockPolicy.findBlockedBy(userId, any()) } returns setOf(blockerId)

            handler.afterConnectionEstablished(createSession(generateValidToken()))

            verify(exactly = 0) { sessionManager.sendToUser(blockerId, any()) }
        }

        @Test
        fun `should still tell everyone else that this user came online`() {
            // A block costs the blocker the feed and nobody else: the fan-out must narrow, not stop.
            every { blockPolicy.findBlockedBy(userId, any()) } returns setOf(blockerId)

            handler.afterConnectionEstablished(createSession(generateValidToken()))

            verify(exactly = 1) { sessionManager.sendToUser(friendId, any()) }
        }

        @Test
        fun `should not leak the last seen stamp to someone who blocked this user`() {
            // The OFFLINE frame carries lastSeenAt. Guarding the connect path alone would still
            // hand a blocked harasser a timestamp every time the victim closed the app.
            every { blockPolicy.findBlockedBy(userId, any()) } returns setOf(blockerId)
            val session = createSession()
            every { sessionManager.getUserId(session) } returns userId
            every { sessionManager.isOnline(userId) } returns false

            handler.afterConnectionClosed(session, CloseStatus.NORMAL)

            verify(exactly = 0) { sessionManager.sendToUser(blockerId, any()) }
            verify(exactly = 1) { sessionManager.sendToUser(friendId, any()) }
        }

        @Test
        fun `should ask about the whole contact set in one call rather than one per contact`() {
            // Contract, not decoration: the chat list resolves every contact on open, and a
            // per-contact question here is an N+1 on the app's busiest moment.
            every { blockPolicy.findBlockedBy(userId, any()) } returns emptySet()

            handler.afterConnectionEstablished(createSession(generateValidToken()))

            verify(exactly = 1) { blockPolicy.findBlockedBy(userId, setOf(blockerId, friendId)) }
        }
    }
}

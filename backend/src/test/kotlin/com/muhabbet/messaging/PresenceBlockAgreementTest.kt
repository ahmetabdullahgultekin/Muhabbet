package com.muhabbet.messaging

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.adapter.`in`.web.ConversationController
import com.muhabbet.messaging.adapter.`in`.websocket.ChatWebSocketHandler
import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.port.`in`.ConversationPage
import com.muhabbet.messaging.domain.port.`in`.ConversationSummary
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.messaging.domain.port.`in`.GetConversationsUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.CallRoomProvider
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.messaging.domain.service.CallSignalingService
import com.muhabbet.messaging.domain.service.PresenceVisibility
import com.muhabbet.shared.TestData
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.JwtClaims
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.security.WebSocketRateLimiter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.net.URI
import java.util.UUID

/**
 * One block, both transports, both directions — the test that was missing while #711 was open.
 *
 * Presence reaches the phone two ways: the `isOnline` flag on `GET /conversations`, and the live
 * `presence.online` / `presence.typing` frames the WebSocket pushes. `ConversationListScreen` writes
 * both into the same `onlineUsers` map, so whichever speaks last wins on screen.
 *
 * They spoke opposite answers. REST filtered on "who has blocked me" and the WebSocket filtered on
 * "who have I blocked", so each transport suppressed exactly the direction the other handed back:
 * the blocked person opened the list to no dot and watched it light up seconds after the blocker
 * connected. Two green suites, one leak — because each test only ever exercised its own transport.
 *
 * So this drives them together, from a single fact (**A has blocked B**) through a single
 * [BlockPolicyPort] that answers the two directional questions honestly. Nothing here stubs a
 * verdict; the fake knows only who blocked whom, and every assertion below is the production code
 * deciding what to do about it.
 */
class PresenceBlockAgreementTest {

    /**
     * Answers only what the `blocks` table would. Deliberately not a mock: a mock is stubbed per
     * call, so it can be told that A blocked B on one transport and not on the other — which is
     * precisely the disagreement this test exists to catch.
     */
    private class FakeBlockPolicy(private val blocks: MutableSet<Pair<UUID, UUID>>) : BlockPolicyPort {
        override fun hasBlocked(blockerId: UUID, blockedId: UUID): Boolean =
            (blockerId to blockedId) in blocks

        override fun findBlockedBy(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> =
            candidateIds.filterTo(mutableSetOf()) { (it to userId) in blocks }

        override fun findBlockedAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> =
            candidateIds.filterTo(mutableSetOf()) { (userId to it) in blocks }
    }

    private val blocker = TestData.USER_ID_1
    private val blocked = TestData.USER_ID_2
    private val convId = TestData.CONVERSATION_ID

    /** The one fact under test. Emptied by the last case, which asserts the guard is not a no-op. */
    private val blocks = mutableSetOf(blocker to blocked)
    private val blockPolicy = FakeBlockPolicy(blocks)

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var presencePort: PresencePort
    private lateinit var userRepository: UserRepository
    private lateinit var sessionManager: WebSocketSessionManager
    private lateinit var jwtProvider: JwtProvider
    private lateinit var controller: ConversationController
    private lateinit var handler: ChatWebSocketHandler

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk(relaxed = true)
        presencePort = mockk(relaxed = true)
        userRepository = mockk()
        sessionManager = mockk(relaxed = true)

        jwtProvider = JwtProvider(
            JwtProperties(
                secret = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256",
                accessTokenExpiry = 900,
                refreshTokenExpiry = 2592000,
                issuer = "muhabbet-test"
            ),
            MockEnvironment()
        )

        val getConversationsUseCase = mockk<GetConversationsUseCase>()
        controller = ConversationController(
            createConversationUseCase = mockk<CreateConversationUseCase>(),
            getConversationsUseCase = getConversationsUseCase,
            manageGroupUseCase = mockk<ManageGroupUseCase>(),
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            presencePort = presencePort,
            presenceVisibility = PresenceVisibility(blockPolicy)
        )

        val rateLimiter = mockk<WebSocketRateLimiter>()
        every { rateLimiter.allowMessage(any()) } returns true
        handler = ChatWebSocketHandler(
            jwtProvider = jwtProvider,
            sessionManager = sessionManager,
            sendMessageUseCase = mockk<SendMessageUseCase>(relaxed = true),
            updateDeliveryStatusUseCase = mockk<UpdateDeliveryStatusUseCase>(relaxed = true),
            conversationRepository = conversationRepository,
            presencePort = presencePort,
            userRepository = mockk(relaxed = true),
            callSignalingService = mockk<CallSignalingService>(relaxed = true),
            callRoomProvider = mockk<CallRoomProvider>(relaxed = true),
            webSocketRateLimiter = rateLimiter,
            presenceVisibility = PresenceVisibility(blockPolicy)
        )

        stubAWorldWithNoReasonToHideAnything(getConversationsUseCase)
    }

    /**
     * Both are genuinely online and share one direct conversation, and every dependency answers as
     * permissively as it can. The block is the only thing in the fixture that may change an answer.
     */
    private fun stubAWorldWithNoReasonToHideAnything(getConversationsUseCase: GetConversationsUseCase) {
        every { presencePort.getOnlineUserIds(any()) } returns setOf(blocker, blocked)
        every { sessionManager.isOnline(any()) } returns true
        every { userRepository.findAllByIds(any()) } returns listOf(
            TestData.user(id = blocker, phoneNumber = TestData.PHONE_1),
            TestData.user(id = blocked, phoneNumber = TestData.PHONE_2, displayName = "Other")
        )
        every { conversationRepository.findAllContactUserIds(blocker) } returns setOf(blocked)
        every { conversationRepository.findAllContactUserIds(blocked) } returns setOf(blocker)
        every { conversationRepository.findMembersByConversationId(convId) } returns listOf(
            ConversationMember(conversationId = convId, userId = blocker),
            ConversationMember(conversationId = convId, userId = blocked)
        )
        every { getConversationsUseCase.getConversations(any(), null, 20) } returns ConversationPage(
            items = listOf(
                ConversationSummary(
                    conversationId = convId,
                    type = "direct",
                    name = null,
                    avatarUrl = null,
                    lastMessagePreview = null,
                    lastMessageAt = null,
                    unreadCount = 0,
                    participantIds = listOf(blocker, blocked)
                )
            ),
            nextCursor = null,
            hasMore = false
        )
    }

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    // ─── Driving the two transports ─────────────────────────

    /** What `GET /conversations` tells [viewer] about [subject]'s dot. */
    private fun restSaysOnline(viewer: UUID, subject: UUID): Boolean {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            JwtClaims(userId = viewer, deviceId = TestData.DEVICE_ID_1),
            null,
            emptyList()
        )
        val page = controller.getConversations(null, 20).body?.data
        return page?.items?.single()?.participants
            ?.single { it.userId == subject.toString() }?.isOnline ?: false
    }

    private fun session(userId: UUID? = null, token: String? = null): WebSocketSession {
        val socket = mockk<WebSocketSession>(relaxed = true)
        every { socket.uri } returns URI(if (token != null) "wss://localhost/ws?token=$token" else "wss://localhost/ws")
        every { socket.id } returns UUID.randomUUID().toString()
        every { socket.isOpen } returns true
        every { socket.attributes } returns
            (if (userId != null) mutableMapOf<String, Any>("userId" to userId) else mutableMapOf())
        return socket
    }

    /** Connects [userId], which is what pushes their `presence.online` frame to their contacts. */
    private fun connect(userId: UUID) {
        handler.afterConnectionEstablished(
            session(token = jwtProvider.generateAccessToken(userId, TestData.DEVICE_ID_1))
        )
    }

    private fun type(userId: UUID) {
        val json = wsJson.encodeToString<WsMessage>(
            WsMessage.TypingIndicator(conversationId = convId.toString(), isTyping = true)
        )
        handler.handleMessage(session(userId = userId), TextMessage(json))
    }

    // ─── The blocker's side ─────────────────────────────────

    @Test
    fun `the blocker is not shown the blocked person's dot over REST`() {
        assertFalse(restSaysOnline(viewer = blocker, subject = blocked))
    }

    @Test
    fun `the blocker is not sent the blocked person's presence over the WebSocket`() {
        connect(blocked)

        verify(exactly = 0) { sessionManager.sendToUser(blocker, any()) }
    }

    @Test
    fun `the blocker is not sent the blocked person's typing indicator`() {
        type(blocked)

        verify(exactly = 0) { sessionManager.sendToUser(blocker, any()) }
    }

    // ─── The blocked person's side ──────────────────────────

    @Test
    fun `the blocked person is not shown the blocker's dot over REST`() {
        assertFalse(restSaysOnline(viewer = blocked, subject = blocker))
    }

    @Test
    fun `the blocked person is not sent the blocker's presence over the WebSocket`() {
        // This is the frame that undid the REST guard: the list opened with no dot and lit up
        // seconds later, because the two transports filtered opposite directions.
        connect(blocker)

        verify(exactly = 0) { sessionManager.sendToUser(blocked, any()) }
    }

    @Test
    fun `the blocked person is not sent the blocker's typing indicator`() {
        type(blocker)

        verify(exactly = 0) { sessionManager.sendToUser(blocked, any()) }
    }

    // ─── And none of it fires without a block ───────────────

    @Test
    fun `two users who have not blocked each other still see each other on both transports`() {
        // Without this, a guard that hid everyone's presence from everyone would pass every
        // assertion above.
        blocks.clear()

        assertTrue(restSaysOnline(viewer = blocker, subject = blocked))
        assertTrue(restSaysOnline(viewer = blocked, subject = blocker))

        connect(blocker)
        verify(exactly = 1) { sessionManager.sendToUser(blocked, any()) }

        type(blocked)
        verify(exactly = 1) { sessionManager.sendToUser(blocker, any()) }
    }
}

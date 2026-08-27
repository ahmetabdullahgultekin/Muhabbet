package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.port.`in`.ConversationPage
import com.muhabbet.messaging.domain.port.`in`.ConversationSummary
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.messaging.domain.port.`in`.GetConversationsUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.TestData
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * `GET /conversations` is where a blocked person actually watches you (#551).
 *
 * The mobile chat list seeds its online dot straight from `ParticipantResponse.isOnline` on this
 * response — `ConversationListScreen` does `onlineUsers[p.userId] = p.isOnline`. So hiding presence
 * on `GET /users/{id}` alone would have been cosmetic: the harasser never has to open the profile
 * screen, because the list they already have shows the same fact, live.
 *
 * This constructs the real controller rather than asserting against a use-case mock, because a test
 * that never builds the thing it names cannot notice when the thing stops working.
 */
class ConversationPresenceBlockTest {

    private lateinit var createConversationUseCase: CreateConversationUseCase
    private lateinit var getConversationsUseCase: GetConversationsUseCase
    private lateinit var manageGroupUseCase: ManageGroupUseCase
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var presencePort: PresencePort
    private lateinit var blockPolicy: BlockPolicyPort
    private lateinit var controller: ConversationController

    private val me = TestData.USER_ID_1
    private val other = TestData.USER_ID_2

    @BeforeEach
    fun setUp() {
        createConversationUseCase = mockk()
        getConversationsUseCase = mockk()
        manageGroupUseCase = mockk()
        conversationRepository = mockk(relaxed = true)
        userRepository = mockk()
        presencePort = mockk()
        blockPolicy = mockk()

        controller = ConversationController(
            createConversationUseCase = createConversationUseCase,
            getConversationsUseCase = getConversationsUseCase,
            manageGroupUseCase = manageGroupUseCase,
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            presencePort = presencePort,
            blockPolicy = blockPolicy
        )

        every { userRepository.findAllByIds(any()) } returns listOf(
            TestData.user(id = me, phoneNumber = TestData.PHONE_1),
            TestData.user(id = other, phoneNumber = TestData.PHONE_2, displayName = "Other")
        )
        // Both genuinely online — the only thing that may change the answer is the block.
        every { presencePort.getOnlineUserIds(any()) } returns setOf(me, other)
        every { getConversationsUseCase.getConversations(me, null, 20) } returns ConversationPage(
            items = listOf(
                ConversationSummary(
                    conversationId = TestData.CONVERSATION_ID,
                    type = "direct",
                    name = null,
                    avatarUrl = null,
                    lastMessagePreview = null,
                    lastMessageContentType = null,
                    lastMessageAt = null,
                    unreadCount = 0,
                    participantIds = listOf(me, other)
                )
            ),
            nextCursor = null,
            hasMore = false
        )

        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            JwtClaims(userId = me, deviceId = TestData.DEVICE_ID_1),
            null,
            emptyList()
        )
    }

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun otherParticipantIsOnline(): Boolean {
        val page = controller.getConversations(null, 20).body?.data
        assertNotNull(page)
        val participant = page?.items?.single()?.participants?.single { it.userId == other.toString() }
        assertNotNull(participant)
        return participant?.isOnline ?: false
    }

    @Test
    fun `should withhold the online dot for a participant who has blocked the viewer`() {
        every { blockPolicy.findBlockedBy(me, listOf(other)) } returns setOf(other)
        every { blockPolicy.findBlockedAmong(me, listOf(other)) } returns emptySet()

        assertFalse(otherParticipantIsOnline())
    }

    /**
     * The direction this endpoint never covered (#711).
     *
     * A block is not a request to be less visible; it is a request to be done with someone. Asking
     * only "who has blocked me" left the person who pressed Block watching their blocked contact's
     * dot in the chat list every day — the half they were actually asking for.
     */
    @Test
    fun `should withhold the online dot for a participant the viewer has blocked`() {
        every { blockPolicy.findBlockedBy(me, listOf(other)) } returns emptySet()
        every { blockPolicy.findBlockedAmong(me, listOf(other)) } returns setOf(other)

        assertFalse(otherParticipantIsOnline())
    }

    @Test
    fun `should show the online dot when no one has blocked the viewer`() {
        every { blockPolicy.findBlockedBy(me, listOf(other)) } returns emptySet()
        every { blockPolicy.findBlockedAmong(me, listOf(other)) } returns emptySet()

        assertTrue(otherParticipantIsOnline())
    }

    @Test
    fun `should ask about the other participants and never about the viewer`() {
        // The viewer is filtered out before the query: asking whether you have blocked yourself is
        // meaningless, and leaving yourself in would hide your own dot from your own chat list.
        every { blockPolicy.findBlockedBy(me, listOf(other)) } returns emptySet()
        every { blockPolicy.findBlockedAmong(me, listOf(other)) } returns emptySet()

        val page = controller.getConversations(null, 20).body?.data
        val self = page?.items?.single()?.participants?.single { it.userId == me.toString() }

        assertEquals(true, self?.isOnline)
    }

    @Test
    fun `should ask each direction once for the whole page rather than once per participant`() {
        // Contract, not decoration: this is the app's busiest call, and either direction resolved
        // per row would be an N+1 on the screen users open first.
        every { blockPolicy.findBlockedBy(me, listOf(other)) } returns emptySet()
        every { blockPolicy.findBlockedAmong(me, listOf(other)) } returns emptySet()

        controller.getConversations(null, 20)

        verify(exactly = 1) { blockPolicy.findBlockedBy(me, listOf(other)) }
        verify(exactly = 1) { blockPolicy.findBlockedAmong(me, listOf(other)) }
    }
}

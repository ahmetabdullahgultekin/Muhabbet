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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * #655: `ConversationSummary.isMuted/isArchived/isLocked` reached [ConversationService] correctly
 * but the controller never copied them onto [com.muhabbet.shared.dto.ConversationResponse] — the
 * DTO's own `false` defaults rode along instead. [ConversationServiceTest] covers the domain-level
 * resolution off the caller's member row; this covers the second half of the same reader, the
 * mapping the wire response actually goes out with. Real controller, like
 * [ConversationPresenceBlockTest] — a mock-to-mock test would not have caught the missing three
 * lines that caused this bug in the first place.
 */
class ConversationFlagsControllerTest {

    private lateinit var createConversationUseCase: CreateConversationUseCase
    private lateinit var getConversationsUseCase: GetConversationsUseCase
    private lateinit var manageGroupUseCase: ManageGroupUseCase
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var presencePort: PresencePort
    private lateinit var blockPolicy: BlockPolicyPort
    private lateinit var controller: ConversationController

    private val me = TestData.USER_ID_1

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

        every { userRepository.findAllByIds(any()) } returns listOf(TestData.user(id = me))
        every { presencePort.getOnlineUserIds(any()) } returns emptySet()
        every { blockPolicy.findBlockedBy(any(), any()) } returns emptySet()
        every { blockPolicy.findBlockedAmong(any(), any()) } returns emptySet()

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

    private fun summaryWith(
        isPinned: Boolean = false,
        isMuted: Boolean = false,
        isArchived: Boolean = false,
        isLocked: Boolean = false
    ): ConversationSummary = ConversationSummary(
        conversationId = UUID.randomUUID(),
        type = "direct",
        name = null,
        avatarUrl = null,
        lastMessagePreview = null,
        lastMessageContentType = null,
        lastMessageAt = null,
        unreadCount = 0,
        participantIds = listOf(me),
        isPinned = isPinned,
        isMuted = isMuted,
        isArchived = isArchived,
        isLocked = isLocked
    )

    @Test
    fun `should carry isMuted isArchived and isLocked onto the response`() {
        every { getConversationsUseCase.getConversations(me, null, 20) } returns ConversationPage(
            items = listOf(summaryWith(isMuted = true, isArchived = true, isLocked = true)),
            nextCursor = null,
            hasMore = false
        )

        val response = controller.getConversations(null, 20).body?.data?.items?.single()

        assertTrue(response?.isMuted == true)
        assertTrue(response?.isArchived == true)
        assertTrue(response?.isLocked == true)
    }

    @Test
    fun `should leave the flags false when the use case reports them false`() {
        every { getConversationsUseCase.getConversations(me, null, 20) } returns ConversationPage(
            items = listOf(summaryWith()),
            nextCursor = null,
            hasMore = false
        )

        val response = controller.getConversations(null, 20).body?.data?.items?.single()

        assertFalse(response?.isMuted == true)
        assertFalse(response?.isArchived == true)
        assertFalse(response?.isLocked == true)
    }
}

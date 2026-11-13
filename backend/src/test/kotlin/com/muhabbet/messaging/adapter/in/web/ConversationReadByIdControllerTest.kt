package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.port.`in`.ConversationDetail
import com.muhabbet.messaging.domain.port.`in`.GetConversationUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.MemberRole
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * `GET /api/v1/conversations/{id}` (#800).
 *
 * Builds the real controller: what is under test is the mapping — real roles, the
 * announcement-only flag, the caller's own flags and the block-aware presence — not a mock's echo.
 */
class ConversationReadByIdControllerTest {

    private lateinit var getConversationUseCase: GetConversationUseCase
    private lateinit var presencePort: PresencePort
    private lateinit var blockPolicy: BlockPolicyPort
    private lateinit var controller: ConversationController

    private val me = TestData.USER_ID_1
    private val other = TestData.USER_ID_2
    private val groupId = TestData.GROUP_ID

    @BeforeEach
    fun setUp() {
        getConversationUseCase = mockk()
        presencePort = mockk()
        blockPolicy = mockk()
        val userRepository = mockk<UserRepository>()

        controller = ConversationController(
            createConversationUseCase = mockk(),
            getConversationsUseCase = mockk(),
            getConversationUseCase = getConversationUseCase,
            manageGroupUseCase = mockk(),
            conversationRepository = mockk(),
            userRepository = userRepository,
            presencePort = presencePort,
            blockPolicy = blockPolicy
        )

        every { userRepository.findAllByIds(any()) } returns listOf(
            TestData.user(id = me, phoneNumber = TestData.PHONE_1),
            TestData.user(id = other, phoneNumber = TestData.PHONE_2, displayName = "Other")
        )
        every { presencePort.getOnlineUserIds(any()) } returns setOf(me, other)
        every { blockPolicy.findBlockedBy(me, listOf(other)) } returns emptySet()
        every { blockPolicy.findBlockedAmong(me, listOf(other)) } returns emptySet()

        val viewer = TestData.owner(conversationId = groupId, userId = me).copy(pinned = true, archived = true)
        every { getConversationUseCase.getConversation(me, groupId) } returns ConversationDetail(
            conversation = TestData.groupConversation().copy(announcementOnly = true, disappearAfterSeconds = 86400),
            members = listOf(viewer, TestData.admin(conversationId = groupId, userId = other)),
            viewer = viewer,
            lastMessage = TestData.textMessage(conversationId = groupId, content = "x".repeat(150)),
            unreadCount = 2
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

    @Test
    fun `should report each participant's real role, not MEMBER for everyone as the list does`() {
        val body = controller.getConversation(groupId).body?.data

        assertEquals(MemberRole.OWNER, body?.participants?.single { it.userId == me.toString() }?.role)
        assertEquals(MemberRole.ADMIN, body?.participants?.single { it.userId == other.toString() }?.role)
    }

    @Test
    fun `should carry the announcement flag, the timer and the caller's own flags`() {
        val body = controller.getConversation(groupId).body?.data

        assertEquals(true, body?.announcementOnly)
        assertEquals(86400, body?.disappearAfterSeconds)
        assertEquals(true, body?.isPinned)
        assertEquals(true, body?.isArchived)
        assertEquals(false, body?.isMuted)
        assertEquals(2, body?.unreadCount)
        assertEquals(100, body?.lastMessagePreview?.length)
    }

    @Test
    fun `should withhold the online dot of a participant in a block relationship with the caller`() {
        every { blockPolicy.findBlockedAmong(me, listOf(other)) } returns setOf(other)

        val body = controller.getConversation(groupId).body?.data

        assertFalse(body?.participants?.single { it.userId == other.toString() }?.isOnline ?: true)
        assertTrue(body?.participants?.single { it.userId == me.toString() }?.isOnline ?: false)
    }

    @Test
    fun `should answer 404 CONV_NOT_FOUND for a conversation the caller is not in`() {
        val notMine = java.util.UUID.randomUUID()
        every { getConversationUseCase.getConversation(me, notMine) } throws
            BusinessException(ErrorCode.CONV_NOT_FOUND)

        val ex = assertThrows<BusinessException> { controller.getConversation(notMine) }

        assertEquals(ErrorCode.CONV_NOT_FOUND, ex.errorCode)
        assertEquals(HttpStatus.NOT_FOUND, ex.errorCode.httpStatus)
    }
}

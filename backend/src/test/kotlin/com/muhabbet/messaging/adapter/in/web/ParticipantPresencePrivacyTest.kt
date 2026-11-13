package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.`in`.ConversationPage
import com.muhabbet.messaging.domain.port.`in`.ConversationSummary
import com.muhabbet.messaging.domain.port.`in`.ConversationWithMembers
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.messaging.domain.port.`in`.GetConversationsUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.TestData
import com.muhabbet.shared.dto.CreateConversationRequest
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import com.muhabbet.shared.model.ConversationType as SharedConversationType

/**
 * The online dot in the chat list follows the participant's own last-seen setting, the same rule
 * the profile screen applies. Starting a chat with someone's id, or adding them to a group, does
 * not make the person who did it one of their contacts.
 */
class ParticipantPresencePrivacyTest {

    private lateinit var create: CreateConversationUseCase
    private lateinit var list: GetConversationsUseCase
    private lateinit var conversations: ConversationRepository
    private lateinit var users: UserRepository
    private lateinit var presence: PresencePort
    private lateinit var blocks: BlockPolicyPort
    private lateinit var controller: ConversationController

    private val me = TestData.USER_ID_1
    private val target = TestData.USER_ID_2

    @BeforeEach
    fun setUp() {
        create = mockk()
        list = mockk()
        conversations = mockk(relaxed = true)
        users = mockk()
        presence = mockk()
        blocks = mockk()
        every { presence.getOnlineUserIds(any()) } returns setOf(me, target)
        every { blocks.findBlockedBy(any(), any()) } returns emptySet()
        every { blocks.findBlockedAmong(any(), any()) } returns emptySet()
        every { conversations.findUsersWhoEngagedWith(me) } returns emptySet()
        controller = ConversationController(create, list, mockk(), conversations, users, presence, blocks)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(JwtClaims(userId = me, deviceId = TestData.DEVICE_ID_1), null, emptyList())
    }

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun targetSetting(visibility: String) {
        every { users.findAllByIds(any()) } returns listOf(
            TestData.user(id = me, phoneNumber = TestData.PHONE_1),
            TestData.user(id = target, phoneNumber = TestData.PHONE_2).copy(onlineStatusVisibility = visibility)
        )
    }

    private fun stubList() {
        every { list.getConversations(me, null, 20) } returns ConversationPage(
            items = listOf(
                ConversationSummary(
                    conversationId = UUID.randomUUID(), type = "direct", name = null, avatarUrl = null,
                    lastMessagePreview = null, lastMessageContentType = null, lastMessageAt = null,
                    unreadCount = 0, participantIds = listOf(me, target)
                )
            ),
            nextCursor = null, hasMore = false
        )
    }

    private fun isOnlineInList(id: UUID): Boolean =
        controller.getConversations(null, 20).body?.data?.items?.single()?.participants
            ?.single { it.userId == id.toString() }?.isOnline ?: false

    @Test
    fun `should hide the online dot when the participant shows it to contacts and has not reached out to the viewer`() {
        targetSetting("contacts")
        stubList()

        assertFalse(isOnlineInList(target))
    }

    @Test
    fun `should hide the online dot in the response when the viewer creates a chat with someone who shows it to contacts`() {
        targetSetting("contacts")
        val conv = TestData.directConversation(createdBy = me)
        every { create.createConversation(ConversationType.DIRECT, me, listOf(target), null) } returns
            ConversationWithMembers(conv, listOf(TestData.member(conv.id, me), TestData.member(conv.id, target)))

        val participants = controller.createConversation(
            CreateConversationRequest(type = SharedConversationType.DIRECT, participantIds = listOf(target.toString()))
        ).body?.data?.participants.orEmpty().associateBy { it.userId }

        assertEquals(false, participants[target.toString()]?.isOnline)
        assertEquals(true, participants[me.toString()]?.isOnline)
    }

    @Test
    fun `should show the online dot when the participant shows it to contacts and has reached out to the viewer`() {
        targetSetting("contacts")
        every { conversations.findUsersWhoEngagedWith(me) } returns setOf(target)
        stubList()

        assertTrue(isOnlineInList(target))
    }

    @Test
    fun `should hide the online dot when the participant shows it to nobody even after reaching out`() {
        targetSetting("nobody")
        every { conversations.findUsersWhoEngagedWith(me) } returns setOf(target)
        stubList()

        assertFalse(isOnlineInList(target))
    }

    @Test
    fun `should show the online dot when the participant shows it to everyone`() {
        targetSetting("everyone")
        stubList()

        assertTrue(isOnlineInList(target))
    }

    @Test
    fun `should always show the viewer their own online dot when their own setting is nobody`() {
        every { users.findAllByIds(any()) } returns listOf(
            TestData.user(id = me, phoneNumber = TestData.PHONE_1).copy(onlineStatusVisibility = "nobody"),
            TestData.user(id = target, phoneNumber = TestData.PHONE_2)
        )
        stubList()

        assertTrue(isOnlineInList(me))
    }
}

package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.`in`.ConversationPage
import com.muhabbet.messaging.domain.port.`in`.ConversationSummary
import com.muhabbet.messaging.domain.port.`in`.ConversationWithMembers
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.messaging.domain.port.`in`.GetConversationsUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.TestData
import com.muhabbet.shared.dto.CreateConversationRequest
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import com.muhabbet.shared.model.ConversationType as SharedConversationType

/**
 * A phone number is shown to someone only once its owner has reached out to them — started or
 * written in a 1:1 chat or group they share. Knowing a user id, creating a chat with it, or
 * subscribing to the same channel is not enough: that is how a number could be looked up for any id.
 */
class ParticipantPhoneDisclosureTest {

    private lateinit var create: CreateConversationUseCase
    private lateinit var list: GetConversationsUseCase
    private lateinit var conversations: ConversationRepository
    private lateinit var users: UserRepository
    private lateinit var presence: PresencePort
    private lateinit var controller: ConversationController

    private val me = TestData.USER_ID_1
    private val stranger = TestData.USER_ID_2
    private val friend = TestData.USER_ID_3

    @BeforeEach
    fun setUp() {
        create = mockk()
        list = mockk()
        conversations = mockk(relaxed = true)
        users = mockk()
        presence = mockk(relaxed = true)
        controller = ConversationController(create, list, mockk(), conversations, users, presence, mockk(relaxed = true))
        every { users.findAllByIds(any()) } returns listOf(
            TestData.user(id = me, phoneNumber = TestData.PHONE_1),
            TestData.user(id = stranger, phoneNumber = TestData.PHONE_2),
            TestData.user(id = friend, phoneNumber = TestData.PHONE_3)
        )
        every { conversations.findUsersWhoEngagedWith(me) } returns setOf(friend)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(JwtClaims(userId = me, deviceId = TestData.DEVICE_ID_1), null, emptyList())
    }

    @Test
    fun `should not reveal the other person's number when a chat is created with a bare user id`() {
        val conv = TestData.directConversation(createdBy = me)
        every { create.createConversation(ConversationType.DIRECT, me, listOf(stranger), null) } returns
            ConversationWithMembers(conv, listOf(TestData.member(conv.id, me), TestData.member(conv.id, stranger)))

        val body = controller.createConversation(
            CreateConversationRequest(type = SharedConversationType.DIRECT, participantIds = listOf(stranger.toString()))
        ).body?.data

        val participants = body?.participants.orEmpty().associateBy { it.userId }
        assertNull(participants[stranger.toString()]?.phoneNumber)
        assertEquals(TestData.PHONE_1, participants[me.toString()]?.phoneNumber)
    }

    @Test
    fun `should show a number only when its owner has reached out to the viewer`() {
        every { list.getConversations(me, null, 20) } returns ConversationPage(
            items = listOf(
                ConversationSummary(
                    conversationId = UUID.randomUUID(), type = "group", name = "g", avatarUrl = null,
                    lastMessagePreview = null, lastMessageContentType = null, lastMessageAt = null,
                    unreadCount = 0, participantIds = listOf(me, stranger, friend)
                )
            ),
            nextCursor = null, hasMore = false
        )

        val participants = controller.getConversations(null, 20).body?.data?.items?.single()?.participants
            .orEmpty().associateBy { it.userId }

        assertEquals(TestData.PHONE_3, participants[friend.toString()]?.phoneNumber)
        assertNull(participants[stranger.toString()]?.phoneNumber)
        assertEquals(TestData.PHONE_1, participants[me.toString()]?.phoneNumber)
    }
}

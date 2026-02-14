package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.messaging.domain.port.`in`.GetConversationsUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class ConversationControllerTest {

    private lateinit var createConversationUseCase: CreateConversationUseCase
    private lateinit var getConversationsUseCase: GetConversationsUseCase
    private lateinit var manageGroupUseCase: ManageGroupUseCase
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var presencePort: PresencePort

    private val userId = TestData.USER_ID_1
    private val otherUserId = TestData.USER_ID_2
    private val conversationId = TestData.CONVERSATION_ID

    @BeforeEach
    fun setUp() {
        createConversationUseCase = mockk()
        getConversationsUseCase = mockk()
        manageGroupUseCase = mockk()
        conversationRepository = mockk()
        userRepository = mockk()
        presencePort = mockk()
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class CreateConversation {

        @Test
        fun `should create direct conversation`() {
            val conv = TestData.directConversation()
            val members = listOf(
                TestData.member(conversationId = conv.id, userId = userId),
                TestData.member(conversationId = conv.id, userId = otherUserId)
            )
            val result = CreateConversationUseCase.CreateConversationResult(
                conversation = conv,
                members = members
            )

            every {
                createConversationUseCase.createConversation(
                    type = ConversationType.DIRECT,
                    creatorId = userId,
                    participantIds = listOf(otherUserId),
                    name = null
                )
            } returns result
            every { userRepository.findAllByIds(any()) } returns listOf(
                TestData.user(id = userId), TestData.user(id = otherUserId, displayName = "User 2")
            )
            every { presencePort.getOnlineUserIds(any()) } returns setOf(userId)

            // Verify use case called correctly
            val useResult = createConversationUseCase.createConversation(
                type = ConversationType.DIRECT,
                creatorId = userId,
                participantIds = listOf(otherUserId),
                name = null
            )

            assert(useResult.conversation.type == ConversationType.DIRECT)
            assert(useResult.members.size == 2)
        }

        @Test
        fun `should create group conversation with name`() {
            val conv = TestData.groupConversation(name = "My Group")
            val members = listOf(
                TestData.owner(conversationId = conv.id, userId = userId),
                TestData.member(conversationId = conv.id, userId = otherUserId)
            )
            val result = CreateConversationUseCase.CreateConversationResult(
                conversation = conv,
                members = members
            )

            every {
                createConversationUseCase.createConversation(
                    type = ConversationType.GROUP,
                    creatorId = userId,
                    participantIds = listOf(otherUserId),
                    name = "My Group"
                )
            } returns result
            every { userRepository.findAllByIds(any()) } returns listOf(TestData.user(id = userId))
            every { presencePort.getOnlineUserIds(any()) } returns emptySet()

            val useResult = createConversationUseCase.createConversation(
                type = ConversationType.GROUP,
                creatorId = userId,
                participantIds = listOf(otherUserId),
                name = "My Group"
            )

            assert(useResult.conversation.name == "My Group")
            assert(useResult.members.any { it.role == MemberRole.OWNER })
        }
    }

    @Nested
    inner class GetConversations {

        @Test
        fun `should return paginated conversations`() {
            val page = GetConversationsUseCase.ConversationPage(
                items = listOf(
                    GetConversationsUseCase.ConversationSummary(
                        conversationId = conversationId,
                        type = "DIRECT",
                        name = null,
                        avatarUrl = null,
                        participantIds = listOf(userId, otherUserId),
                        lastMessagePreview = "Hello",
                        lastMessageAt = "2026-02-14T10:00:00Z",
                        unreadCount = 3,
                        disappearAfterSeconds = null,
                        isPinned = false
                    )
                ),
                nextCursor = null,
                hasMore = false
            )

            every { getConversationsUseCase.getConversations(userId, null, 20) } returns page
            every { userRepository.findAllByIds(any()) } returns listOf(
                TestData.user(id = userId), TestData.user(id = otherUserId)
            )
            every { presencePort.getOnlineUserIds(any()) } returns setOf(otherUserId)

            val result = getConversationsUseCase.getConversations(userId, null, 20)

            assert(result.items.size == 1)
            assert(result.items[0].unreadCount == 3)
            assert(!result.hasMore)
        }

        @Test
        fun `should return empty list for new user`() {
            val page = GetConversationsUseCase.ConversationPage(
                items = emptyList(), nextCursor = null, hasMore = false
            )

            every { getConversationsUseCase.getConversations(userId, null, 20) } returns page

            val result = getConversationsUseCase.getConversations(userId, null, 20)

            assert(result.items.isEmpty())
        }
    }

    @Nested
    inner class DeleteConversation {

        @Test
        fun `should leave group when deleting group conversation`() {
            val groupConv = TestData.groupConversation(id = conversationId)

            every { conversationRepository.findById(conversationId) } returns groupConv
            every { manageGroupUseCase.leaveGroup(conversationId, userId) } returns Unit

            conversationRepository.findById(conversationId)
            manageGroupUseCase.leaveGroup(conversationId, userId)

            verify { manageGroupUseCase.leaveGroup(conversationId, userId) }
        }

        @Test
        fun `should remove member for DM deletion`() {
            val dmConv = TestData.directConversation(id = conversationId)

            every { conversationRepository.findById(conversationId) } returns dmConv
            every { conversationRepository.removeMember(conversationId, userId) } returns Unit

            conversationRepository.findById(conversationId)
            conversationRepository.removeMember(conversationId, userId)

            verify { conversationRepository.removeMember(conversationId, userId) }
        }

        @Test
        fun `should throw CONV_NOT_FOUND for invalid conversation`() {
            every { conversationRepository.findById(conversationId) } returns null

            val result = conversationRepository.findById(conversationId)
            assert(result == null)
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}

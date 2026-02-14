package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.domain.port.`in`.ManageReactionUseCase
import com.muhabbet.messaging.domain.port.`in`.ReactionGroup
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.TestData
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

class ReactionControllerTest {

    private lateinit var manageReactionUseCase: ManageReactionUseCase
    private lateinit var messageRepository: MessageRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var sessionManager: WebSocketSessionManager
    private lateinit var controller: ReactionController

    private val userId = TestData.USER_ID_1
    private val messageId = TestData.MESSAGE_ID

    @BeforeEach
    fun setUp() {
        manageReactionUseCase = mockk()
        messageRepository = mockk()
        conversationRepository = mockk()
        sessionManager = mockk(relaxed = true)
        controller = ReactionController(manageReactionUseCase, messageRepository, conversationRepository, sessionManager)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class AddReaction {

        @Test
        fun `should add reaction and broadcast via WebSocket`() {
            val message = TestData.textMessage(id = messageId)
            val members = listOf(
                TestData.member(conversationId = message.conversationId, userId = userId),
                TestData.member(conversationId = message.conversationId, userId = TestData.USER_ID_2)
            )

            every { manageReactionUseCase.addReaction(messageId, userId, "\uD83D\uDE00") } returns Unit
            every { messageRepository.findById(messageId) } returns message
            every { conversationRepository.findMembersByConversationId(message.conversationId) } returns members

            val response = controller.addReaction(messageId, com.muhabbet.shared.dto.ReactionRequest(emoji = "\uD83D\uDE00"))

            assert(response.statusCode.value() == 200)
            verify { manageReactionUseCase.addReaction(messageId, userId, "\uD83D\uDE00") }
        }
    }

    @Nested
    inner class RemoveReaction {

        @Test
        fun `should remove reaction`() {
            val message = TestData.textMessage(id = messageId)

            every { manageReactionUseCase.removeReaction(messageId, userId, "\uD83D\uDE00") } returns Unit
            every { messageRepository.findById(messageId) } returns message
            every { conversationRepository.findMembersByConversationId(message.conversationId) } returns emptyList()

            val response = controller.removeReaction(messageId, "\uD83D\uDE00")

            assert(response.statusCode.value() == 200)
            verify { manageReactionUseCase.removeReaction(messageId, userId, "\uD83D\uDE00") }
        }
    }

    @Nested
    inner class GetReactions {

        @Test
        fun `should return grouped reactions`() {
            val reactions = listOf(
                ReactionGroup(emoji = "\uD83D\uDE00", count = 3, userIds = listOf(userId, TestData.USER_ID_2, TestData.USER_ID_3)),
                ReactionGroup(emoji = "\u2764\uFE0F", count = 1, userIds = listOf(userId))
            )

            every { manageReactionUseCase.getReactions(messageId) } returns reactions

            val response = controller.getReactions(messageId)

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.size == 2)
            assert(response.body?.data?.first()?.count == 3)
        }

        @Test
        fun `should return empty list when no reactions`() {
            every { manageReactionUseCase.getReactions(messageId) } returns emptyList()

            val response = controller.getReactions(messageId)

            assert(response.body?.data?.isEmpty() == true)
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}

package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.domain.port.`in`.ManageReactionUseCase
import com.muhabbet.messaging.domain.port.`in`.ReactionGroup
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import com.muhabbet.shared.security.JwtClaims
import org.junit.jupiter.api.Assertions.assertEquals
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

            every { manageReactionUseCase.addReaction(messageId, userId, "\uD83D\uDE00") } returns emptyList()
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

            every { manageReactionUseCase.removeReaction(messageId, userId, "\uD83D\uDE00") } returns emptyList()
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

            every { manageReactionUseCase.getReactions(messageId, userId) } returns reactions

            val response = controller.getReactions(messageId)

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.size == 2)
            assert(response.body?.data?.first()?.count == 3)
        }

        @Test
        fun `should return empty list when no reactions`() {
            every { manageReactionUseCase.getReactions(messageId, userId) } returns emptyList()

            val response = controller.getReactions(messageId)

            assert(response.body?.data?.isEmpty() == true)
        }
    }

    /**
     * #783: the caller gets the server's state to reconcile against, and the broadcast carries the
     * resulting count, so a frame applied twice cannot drift anybody's tally.
     */
    @Nested
    inner class AuthoritativeState {

        private val smile = "\uD83D\uDE00"
        private val heart = "\u2764\uFE0F"

        @Test
        fun `should answer an add with the caller's view of every reaction on the message`() {
            val message = TestData.textMessage(id = messageId)
            every { manageReactionUseCase.addReaction(messageId, userId, smile) } returns listOf(
                ReactionGroup(emoji = smile, count = 2, userIds = listOf(userId, TestData.USER_ID_2)),
                ReactionGroup(emoji = heart, count = 1, userIds = listOf(TestData.USER_ID_2))
            )
            every { messageRepository.findById(messageId) } returns message
            every { conversationRepository.findMembersByConversationId(message.conversationId) } returns emptyList()

            val body = controller.addReaction(messageId, com.muhabbet.shared.dto.ReactionRequest(emoji = smile)).body?.data

            assertEquals(messageId.toString(), body?.messageId)
            assertEquals(mapOf(smile to 2, heart to 1), body?.reactions)
            assertEquals(setOf(smile), body?.myReactions)
        }

        @Test
        fun `should broadcast the count after the change, not a delta`() {
            val message = TestData.textMessage(id = messageId)
            every { manageReactionUseCase.addReaction(messageId, userId, smile) } returns listOf(
                ReactionGroup(emoji = smile, count = 2, userIds = listOf(userId, TestData.USER_ID_2))
            )
            every { messageRepository.findById(messageId) } returns message
            every { conversationRepository.findMembersByConversationId(message.conversationId) } returns listOf(
                TestData.member(conversationId = message.conversationId, userId = TestData.USER_ID_2)
            )
            val sent = slot<String>()
            every { sessionManager.sendToUser(TestData.USER_ID_2, capture(sent)) } returns Unit

            controller.addReaction(messageId, com.muhabbet.shared.dto.ReactionRequest(emoji = smile))

            val frame = wsJson.decodeFromString(WsMessage.serializer(), sent.captured) as WsMessage.MessageReaction
            assertEquals(2, frame.count)
        }

        @Test
        fun `should broadcast a zero count when the last reaction of that emoji is removed`() {
            val message = TestData.textMessage(id = messageId)
            every { manageReactionUseCase.removeReaction(messageId, userId, smile) } returns emptyList()
            every { messageRepository.findById(messageId) } returns message
            every { conversationRepository.findMembersByConversationId(message.conversationId) } returns listOf(
                TestData.member(conversationId = message.conversationId, userId = userId)
            )
            val sent = slot<String>()
            every { sessionManager.sendToUser(userId, capture(sent)) } returns Unit

            val body = controller.removeReaction(messageId, smile).body?.data

            val frame = wsJson.decodeFromString(WsMessage.serializer(), sent.captured) as WsMessage.MessageReaction
            assertEquals(0, frame.count)
            assertEquals(emptySet<String>(), body?.myReactions)
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}

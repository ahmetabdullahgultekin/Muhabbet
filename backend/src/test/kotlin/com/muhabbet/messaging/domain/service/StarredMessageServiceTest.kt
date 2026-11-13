package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * Starring is a bookmark, not a way in: a user can only star, and read back, messages from
 * conversations they are still a member of, and never someone else's unreleased scheduled message.
 */
class StarredMessageServiceTest {

    private lateinit var starred: StarredMessageRepository
    private lateinit var messages: MessageRepository
    private lateinit var conversations: ConversationRepository
    private lateinit var service: StarredMessageService

    private val alice = TestData.USER_ID_1
    private val bob = TestData.USER_ID_2

    @BeforeEach
    fun setUp() {
        starred = mockk(relaxed = true)
        messages = mockk()
        conversations = mockk()
        service = StarredMessageService(starred, messages, conversations)
    }

    @Test
    fun `should refuse to star when the caller is not a member of the message's conversation`() {
        val message = TestData.textMessage(senderId = alice)
        every { messages.findById(message.id) } returns message
        every { conversations.findMember(message.conversationId, bob) } returns null

        val ex = assertThrows<BusinessException> { service.star(bob, message.id) }

        assertEquals(ErrorCode.MSG_NOT_FOUND, ex.errorCode)
        verify(exactly = 0) { starred.star(any(), any()) }
    }

    @Test
    fun `should refuse to star when the message does not exist`() {
        val id = UUID.randomUUID()
        every { messages.findById(id) } returns null

        val ex = assertThrows<BusinessException> { service.star(bob, id) }

        assertEquals(ErrorCode.MSG_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should star when the caller is a member`() {
        val message = TestData.textMessage(senderId = alice)
        every { messages.findById(message.id) } returns message
        every { conversations.findMember(message.conversationId, bob) } returns
            TestData.member(message.conversationId, bob)

        service.star(bob, message.id)

        verify { starred.star(bob, message.id) }
    }

    @Test
    fun `should hide starred messages when the caller has left their conversation`() {
        val kept = TestData.textMessage(id = UUID.randomUUID(), conversationId = TestData.CONVERSATION_ID)
        val left = TestData.textMessage(id = UUID.randomUUID(), conversationId = TestData.GROUP_ID)
        every { starred.getStarredMessages(bob, 50, 0) } returns listOf(kept, left)
        every { conversations.findConversationsByUserId(bob) } returns listOf(TestData.directConversation())

        val result = service.getStarredMessages(bob, 50, 0)

        assertEquals(listOf(kept.id), result.map { it.id })
    }

    @Test
    fun `should hide someone else's scheduled message when it was starred before release`() {
        val scheduled = TestData.textMessage(senderId = alice)
            .copy(isScheduled = true, scheduledAt = Instant.now().plusSeconds(3600))
        every { starred.getStarredMessages(bob, 50, 0) } returns listOf(scheduled)
        every { conversations.findConversationsByUserId(bob) } returns listOf(TestData.directConversation())

        assertEquals(emptyList<Any>(), service.getStarredMessages(bob, 50, 0))
    }

    @Test
    fun `should clamp paging when the limit is zero or negative`() {
        every { starred.getStarredMessages(bob, 1, 0) } returns emptyList()

        service.getStarredMessages(bob, 0, -5)

        verify { starred.getStarredMessages(bob, 1, 0) }
    }
}

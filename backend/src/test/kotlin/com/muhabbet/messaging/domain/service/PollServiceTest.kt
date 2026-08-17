package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * A poll vote changes a number that everyone in the conversation sees, and the results tell you
 * what a group you are not in is deciding. Both are reads and writes into a private conversation,
 * reachable with nothing but a message id. See issue #557.
 */
class PollServiceTest {

    private lateinit var messageRepository: MessageRepository
    private lateinit var pollVoteRepository: PollVoteRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var service: PollService

    private val messageId = TestData.MESSAGE_ID
    private val conversationId = TestData.CONVERSATION_ID
    private val member = TestData.USER_ID_1
    private val outsider = TestData.USER_ID_3

    private val pollJson =
        """{"question":"Nerede buluşalım?","options":["Kadıköy","Beşiktaş"],"multipleChoice":false}"""

    @BeforeEach
    fun setUp() {
        messageRepository = mockk()
        pollVoteRepository = mockk(relaxed = true)
        conversationRepository = mockk()
        service = PollService(messageRepository, pollVoteRepository, conversationRepository)

        every { messageRepository.findById(messageId) } returns Message(
            id = messageId,
            conversationId = conversationId,
            senderId = member,
            contentType = ContentType.POLL,
            content = pollJson,
            clientTimestamp = Instant.ofEpochMilli(1786993903882L)
        )
        every { conversationRepository.findMember(conversationId, member) } returns
            TestData.member(conversationId = conversationId, userId = member)
        every { conversationRepository.findMember(conversationId, outsider) } returns null
        every { pollVoteRepository.findByMessageId(messageId) } returns emptyList()
    }

    @Nested
    inner class Membership {

        @Test
        fun `should refuse a vote from someone who is not in the conversation`() {
            val e = assertThrows<BusinessException> { service.vote(messageId, outsider, 0) }

            assertEquals(ErrorCode.MSG_NOT_MEMBER, e.errorCode)
            verify(exactly = 0) { pollVoteRepository.save(any()) }
            verify(exactly = 0) { pollVoteRepository.deleteByMessageIdAndUserId(any(), any()) }
        }

        @Test
        fun `should refuse to hand the results to someone who is not in the conversation`() {
            val e = assertThrows<BusinessException> { service.getResults(messageId, outsider) }

            assertEquals(ErrorCode.MSG_NOT_MEMBER, e.errorCode)
        }

        @Test
        fun `should still let a member vote`() {
            val result = service.vote(messageId, member, 1)

            assertEquals(2, result.options.size)
            verify(exactly = 1) { pollVoteRepository.save(any()) }
        }

        @Test
        fun `should check membership before validating the option, so an outsider learns nothing about the poll`() {
            val e = assertThrows<BusinessException> { service.vote(messageId, outsider, 99) }

            assertEquals(ErrorCode.MSG_NOT_MEMBER, e.errorCode)
        }
    }
}

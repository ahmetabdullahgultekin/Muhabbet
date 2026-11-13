package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PollVote
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
    private lateinit var messageBroadcaster: MessageBroadcaster
    private lateinit var service: PollService

    /** Inline, but remembers whether a broadcast happened while the "transaction" was open. */
    private var insideTransaction = false
    private var broadcastInsideTransaction = false
    private val transactions = object : TransactionRunner {
        override fun <T : Any> inTransaction(block: () -> T): T {
            insideTransaction = true
            try {
                return block()
            } finally {
                insideTransaction = false
            }
        }
    }

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
        messageBroadcaster = mockk()
        every { messageBroadcaster.broadcastToUsers(any(), any()) } answers {
            if (insideTransaction) broadcastInsideTransaction = true
        }
        service = PollService(
            messageRepository,
            pollVoteRepository,
            conversationRepository,
            messageBroadcaster,
            transactions
        )

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
        every { conversationRepository.findMembersByConversationId(conversationId) } returns listOf(
            TestData.member(conversationId = conversationId, userId = member),
            TestData.member(conversationId = conversationId, userId = TestData.USER_ID_2)
        )
    }

    @Nested
    inner class Membership {

        @Test
        fun `should refuse a vote from someone who is not in the conversation`() {
            val e = assertThrows<BusinessException> { service.vote(messageId, outsider, 0) }

            assertEquals(ErrorCode.MSG_NOT_MEMBER, e.errorCode)
            verify(exactly = 0) { pollVoteRepository.upsert(any()) }
            verify(exactly = 0) { messageBroadcaster.broadcastToUsers(any(), any()) }
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
            verify(exactly = 1) { pollVoteRepository.upsert(any()) }
        }

        @Test
        fun `should check membership before validating the option, so an outsider learns nothing about the poll`() {
            val e = assertThrows<BusinessException> { service.vote(messageId, outsider, 99) }

            assertEquals(ErrorCode.MSG_NOT_MEMBER, e.errorCode)
        }
    }

    /** #781: other members' bubbles move when someone votes, instead of staying frozen. */
    @Nested
    inner class LiveUpdate {

        private fun vote(user: java.util.UUID, option: Int) =
            PollVote(messageId = messageId, userId = user, optionIndex = option)

        @Test
        fun `should broadcast the full tally to every member after a vote`() {
            every { pollVoteRepository.findByMessageId(messageId) } returns listOf(
                vote(member, 1),
                vote(TestData.USER_ID_2, 1)
            )
            val recipients = slot<List<java.util.UUID>>()
            val frame = slot<WsMessage>()
            every { messageBroadcaster.broadcastToUsers(capture(recipients), capture(frame)) } returns Unit

            service.vote(messageId, member, 1)

            assertEquals(setOf(member, TestData.USER_ID_2), recipients.captured.toSet())
            val updated = frame.captured as WsMessage.PollUpdated
            assertEquals(listOf(0, 2), updated.counts)
            assertEquals(2, updated.totalVotes)
            assertEquals(member.toString(), updated.voterId)
            assertEquals(1, updated.optionIndex)
            assertEquals(conversationId.toString(), updated.conversationId)
        }

        @Test
        fun `should broadcast only after the vote has committed`() {
            service.vote(messageId, member, 0)

            verify(exactly = 1) { messageBroadcaster.broadcastToUsers(any(), any()) }
            assertEquals(false, broadcastInsideTransaction)
        }

        @Test
        fun `should broadcast nothing for an invalid option`() {
            assertThrows<BusinessException> { service.vote(messageId, member, 7) }

            verify(exactly = 0) { messageBroadcaster.broadcastToUsers(any(), any()) }
        }

        @Test
        fun `should record a change of mind as one upsert, never a delete and an insert`() {
            service.vote(messageId, member, 0)
            service.vote(messageId, member, 1)

            verify(exactly = 2) { pollVoteRepository.upsert(any()) }
        }
    }
}

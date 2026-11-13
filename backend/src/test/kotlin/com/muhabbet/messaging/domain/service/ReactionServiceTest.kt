package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReactionRepository
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
 * A reaction is the one write path that lets a user put content into somebody else's conversation
 * without sending a message, and the controller broadcasts it live to every member. So the tests
 * that matter here are the negative ones: an outsider must not be able to reach the repository at
 * all. See issue #557.
 */
class ReactionServiceTest {

    private lateinit var reactionRepository: ReactionRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var service: ReactionService

    private val messageId = TestData.MESSAGE_ID
    private val conversationId = TestData.CONVERSATION_ID
    private val member = TestData.USER_ID_1
    private val outsider = TestData.USER_ID_3
    private val thumbsUp = "👍"

    @BeforeEach
    fun setUp() {
        reactionRepository = mockk(relaxed = true)
        messageRepository = mockk()
        conversationRepository = mockk()
        service = ReactionService(reactionRepository, messageRepository, conversationRepository)

        every { messageRepository.findById(messageId) } returns Message(
            id = messageId,
            conversationId = conversationId,
            senderId = member,
            content = "merhaba",
            clientTimestamp = Instant.ofEpochMilli(1786993903882L)
        )
        every { conversationRepository.findMember(conversationId, member) } returns
            TestData.member(conversationId = conversationId, userId = member)
        every { conversationRepository.findMember(conversationId, outsider) } returns null
    }

    @Nested
    inner class Membership {

        @Test
        fun `should refuse to add a reaction from someone who is not in the conversation`() {
            val e = assertThrows<BusinessException> {
                service.addReaction(messageId, outsider, thumbsUp)
            }

            assertEquals(ErrorCode.MSG_NOT_MEMBER, e.errorCode)
            verify(exactly = 0) { reactionRepository.addIfAbsent(any()) }
        }

        @Test
        fun `should refuse to remove a reaction from someone who is not in the conversation`() {
            val e = assertThrows<BusinessException> {
                service.removeReaction(messageId, outsider, thumbsUp)
            }

            assertEquals(ErrorCode.MSG_NOT_MEMBER, e.errorCode)
            verify(exactly = 0) {
                reactionRepository.deleteByMessageIdAndUserIdAndEmoji(any(), any(), any())
            }
        }

        @Test
        fun `should refuse to list who reacted to someone else's message`() {
            val e = assertThrows<BusinessException> {
                service.getReactions(messageId, outsider)
            }

            assertEquals(ErrorCode.MSG_NOT_MEMBER, e.errorCode)
            verify(exactly = 0) { reactionRepository.findByMessageId(any()) }
        }

        @Test
        fun `should still let a member react`() {
            service.addReaction(messageId, member, thumbsUp)

            verify(exactly = 1) { reactionRepository.addIfAbsent(any()) }
        }

        @Test
        fun `should report a missing message as not found rather than as a membership failure`() {
            every { messageRepository.findById(messageId) } returns null

            val e = assertThrows<BusinessException> {
                service.addReaction(messageId, member, thumbsUp)
            }

            assertEquals(ErrorCode.MSG_NOT_FOUND, e.errorCode)
        }
    }

    @Nested
    inner class EmojiValidation {

        @Test
        fun `should reject a reaction the picker cannot produce`() {
            val e = assertThrows<BusinessException> {
                service.addReaction(messageId, member, "SIKTIR")
            }

            assertEquals(ErrorCode.MSG_INVALID_REACTION, e.errorCode)
            verify(exactly = 0) { reactionRepository.addIfAbsent(any()) }
        }

        @Test
        fun `should reject a reaction that fills the column with arbitrary text`() {
            val e = assertThrows<BusinessException> {
                service.addReaction(messageId, member, "0123456789abcdef")
            }

            assertEquals(ErrorCode.MSG_INVALID_REACTION, e.errorCode)
        }

        @Test
        fun `should accept every emoji the reaction bar offers`() {
            com.muhabbet.shared.validation.ValidationRules.ALLOWED_REACTIONS.forEach { emoji ->
                service.addReaction(messageId, member, emoji)
            }

            verify(exactly = com.muhabbet.shared.validation.ValidationRules.ALLOWED_REACTIONS.size) {
                reactionRepository.addIfAbsent(any())
            }
        }

        @Test
        fun `should reject an unknown emoji on removal too, so nothing arbitrary reaches the query`() {
            val e = assertThrows<BusinessException> {
                service.removeReaction(messageId, member, "<script>")
            }

            assertEquals(ErrorCode.MSG_INVALID_REACTION, e.errorCode)
        }
    }

    /**
     * #783: a reaction write answers with the server's truth, and a repeat of the same add is a
     * no-op rather than a duplicate — the client reconciles from this answer, and the broadcast count
     * is taken from it.
     */
    @Nested
    inner class AuthoritativeState {

        private val other = TestData.USER_ID_2

        private fun reaction(user: java.util.UUID, emoji: String) =
            com.muhabbet.messaging.domain.model.Reaction(messageId = messageId, userId = user, emoji = emoji)

        @Test
        fun `should answer an add with every reaction on the message after the write`() {
            every { reactionRepository.findByMessageId(messageId) } returns listOf(
                reaction(member, thumbsUp),
                reaction(other, thumbsUp),
                reaction(other, "❤️")
            )

            val groups = service.addReaction(messageId, member, thumbsUp)

            assertEquals(mapOf(thumbsUp to 2, "❤️" to 1), groups.associate { it.emoji to it.count })
            assertEquals(true, member in groups.first { it.emoji == thumbsUp }.userIds)
        }

        @Test
        fun `should answer a repeated add with the same state and write nothing new`() {
            every { reactionRepository.addIfAbsent(any()) } returns false
            every { reactionRepository.findByMessageId(messageId) } returns listOf(reaction(member, thumbsUp))

            val groups = service.addReaction(messageId, member, thumbsUp)

            assertEquals(listOf(1), groups.map { it.count })
            verify(exactly = 1) { reactionRepository.addIfAbsent(any()) }
        }

        @Test
        fun `should answer a removal with what is left`() {
            every { reactionRepository.findByMessageId(messageId) } returns listOf(reaction(other, thumbsUp))

            val groups = service.removeReaction(messageId, member, thumbsUp)

            assertEquals(listOf(1), groups.map { it.count })
            assertEquals(false, member in groups.single().userIds)
        }
    }
}

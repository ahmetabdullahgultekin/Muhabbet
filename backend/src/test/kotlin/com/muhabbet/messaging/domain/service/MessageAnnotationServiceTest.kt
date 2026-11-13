package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PollVote
import com.muhabbet.messaging.domain.model.Reaction
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import com.muhabbet.messaging.domain.port.out.ReactionRepository
import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
import com.muhabbet.shared.TestData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * #775, #781, #783: what a history page tells one viewer about each message, resolved in a fixed
 * number of queries however long the page is.
 */
class MessageAnnotationServiceTest {

    private lateinit var starred: StarredMessageRepository
    private lateinit var reactions: ReactionRepository
    private lateinit var votes: PollVoteRepository
    private lateinit var service: MessageAnnotationService

    private val viewer = TestData.USER_ID_1
    private val other = TestData.USER_ID_2
    private val pollBody = """{"question":"Nerede?","options":["Kadıköy","Beşiktaş","Üsküdar"]}"""

    @BeforeEach
    fun setUp() {
        starred = mockk()
        reactions = mockk()
        votes = mockk()
        service = MessageAnnotationService(starred, reactions, votes)
        every { starred.getStarredMessageIds(any(), any()) } returns emptySet()
        every { reactions.findByMessageIds(any()) } returns emptyList()
        every { votes.findByMessageIds(any()) } returns emptyList()
    }

    private fun message(
        contentType: ContentType = ContentType.TEXT,
        content: String = "merhaba",
        isDeleted: Boolean = false
    ) = Message(
        id = UUID.randomUUID(),
        conversationId = TestData.CONVERSATION_ID,
        senderId = other,
        contentType = contentType,
        content = content,
        isDeleted = isDeleted,
        clientTimestamp = Instant.ofEpochMilli(1786993903882L)
    )

    @Nested
    inner class Stars {

        @Test
        fun `should mark only the messages the viewer starred`() {
            val mine = message()
            val notMine = message()
            every { starred.getStarredMessageIds(viewer, listOf(mine.id, notMine.id)) } returns setOf(mine.id)

            val result = service.resolveAnnotations(listOf(mine, notMine), viewer)

            assertTrue(result.getValue(mine.id).isStarred)
            assertFalse(result.getValue(notMine.id).isStarred)
        }
    }

    @Nested
    inner class Reactions {

        @Test
        fun `should count every reaction and mark only the viewer's own as mine`() {
            val msg = message()
            every { reactions.findByMessageIds(listOf(msg.id)) } returns listOf(
                Reaction(messageId = msg.id, userId = viewer, emoji = "👍"),
                Reaction(messageId = msg.id, userId = other, emoji = "👍"),
                Reaction(messageId = msg.id, userId = other, emoji = "😂")
            )

            val annotations = service.resolveAnnotations(listOf(msg), viewer).getValue(msg.id)

            assertEquals(mapOf("👍" to 2, "😂" to 1), annotations.reactions)
            assertEquals(setOf("👍"), annotations.myReactions)
        }
    }

    @Nested
    inner class Polls {

        @Test
        fun `should tally a poll with the viewer's own vote`() {
            val poll = message(contentType = ContentType.POLL, content = pollBody)
            every { votes.findByMessageIds(setOf(poll.id)) } returns listOf(
                PollVote(messageId = poll.id, userId = viewer, optionIndex = 2),
                PollVote(messageId = poll.id, userId = other, optionIndex = 0)
            )

            val tally = service.resolveAnnotations(listOf(poll), viewer).getValue(poll.id).poll

            assertEquals(listOf(1, 0, 1), tally?.counts)
            assertEquals(2, tally?.totalVotes)
            assertEquals(2, tally?.myVote)
        }

        @Test
        fun `should not query votes at all for a page without polls`() {
            service.resolveAnnotations(listOf(message(), message()), viewer)

            verify(exactly = 0) { votes.findByMessageIds(any()) }
        }

        @Test
        fun `should give a deleted poll no tally`() {
            val poll = message(contentType = ContentType.POLL, content = "", isDeleted = true)

            assertNull(service.resolveAnnotations(listOf(poll), viewer).getValue(poll.id).poll)
        }

        @Test
        fun `should give an unreadable poll body no tally rather than failing the page`() {
            val broken = message(contentType = ContentType.POLL, content = "not json")
            val fine = message()

            val result = service.resolveAnnotations(listOf(broken, fine), viewer)

            assertNull(result.getValue(broken.id).poll)
            assertEquals(2, result.size)
        }
    }

    @Nested
    inner class QueryBudget {

        @Test
        fun `should cost one query per kind of annotation however long the page is`() {
            val page = List(40) { message() } + message(contentType = ContentType.POLL, content = pollBody)

            service.resolveAnnotations(page, viewer)

            verify(exactly = 1) { starred.getStarredMessageIds(any(), any()) }
            verify(exactly = 1) { reactions.findByMessageIds(any()) }
            verify(exactly = 1) { votes.findByMessageIds(any()) }
        }

        @Test
        fun `should not touch the database for an empty page`() {
            assertTrue(service.resolveAnnotations(emptyList(), viewer).isEmpty())

            verify(exactly = 0) { starred.getStarredMessageIds(any(), any()) }
            verify(exactly = 0) { reactions.findByMessageIds(any()) }
        }
    }

    @Nested
    inner class Tally {

        @Test
        fun `should leave out a vote for an option the poll does not have, from its bars and its total`() {
            val messageId = UUID.randomUUID()
            val tally = tallyPoll(
                optionCount = 2,
                votes = listOf(
                    PollVote(messageId = messageId, userId = viewer, optionIndex = 5),
                    PollVote(messageId = messageId, userId = other, optionIndex = 1)
                ),
                viewerId = viewer
            )

            assertEquals(listOf(0, 1), tally.counts)
            assertEquals(1, tally.totalVotes)
            assertNull(tally.myVote)
        }
    }
}

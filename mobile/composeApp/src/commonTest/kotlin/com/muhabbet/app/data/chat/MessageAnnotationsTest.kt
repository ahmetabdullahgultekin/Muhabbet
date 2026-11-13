package com.muhabbet.app.data.chat

import com.muhabbet.shared.dto.MessageReactionsResponse
import com.muhabbet.shared.dto.PollOptionResult
import com.muhabbet.shared.dto.PollResultResponse
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.PollTally
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The reducers the chat applies reaction, poll and star events through (#775, #781, #783).
 *
 * The property most worth pinning is idempotence under a duplicate: the socket does not deduplicate
 * these frames, and the old ±1 code turned every duplicate into a permanent wrong count.
 */
class MessageAnnotationsTest {

    private val me = "me"
    private val them = "them"
    private val thumbs = "👍"

    private fun message(
        reactions: Map<String, Int> = emptyMap(),
        myReactions: Set<String> = emptySet(),
        poll: PollTally? = null,
    ) = Message(
        id = "m1",
        conversationId = "c1",
        senderId = them,
        contentType = if (poll != null) ContentType.POLL else ContentType.TEXT,
        content = "",
        clientTimestamp = Instant.fromEpochMilliseconds(0),
        reactions = reactions,
        myReactions = myReactions,
        poll = poll,
    )

    private fun reactionFrame(userId: String, action: String, count: Int?, emoji: String = thumbs) =
        WsMessage.MessageReaction(
            messageId = "m1",
            conversationId = "c1",
            userId = userId,
            emoji = emoji,
            action = action,
            count = count,
        )

    // ─── Reactions ───────────────────────────────────────────────

    @Test
    fun applyingTheSameAddFrameTwice_leavesCountOne() {
        val frame = reactionFrame(them, "add", count = 1)

        val once = message().applyReactionFrame(frame, me)
        val twice = once.applyReactionFrame(frame, me)

        assertEquals(mapOf(thumbs to 1), twice.reactions)
        assertEquals(once, twice)
    }

    @Test
    fun theEchoOfMyOwnOptimisticAdd_changesNothing() {
        val optimistic = message(reactions = mapOf(thumbs to 1)).toggleReaction(thumbs).optimistic

        val echoed = optimistic.applyReactionFrame(reactionFrame(me, "add", count = 2), me)

        assertEquals(optimistic, echoed)
    }

    @Test
    fun aRemoveFrameToZero_dropsTheEmojiAndOnlyMyFrameTouchesMine() {
        val start = message(reactions = mapOf(thumbs to 2), myReactions = setOf(thumbs))

        val afterTheirs = start.applyReactionFrame(reactionFrame(them, "remove", count = 1), me)
        val afterMine = afterTheirs.applyReactionFrame(reactionFrame(me, "remove", count = 0), me)

        assertEquals(setOf(thumbs), afterTheirs.myReactions)
        assertEquals(emptyMap(), afterMine.reactions)
        assertEquals(emptySet(), afterMine.myReactions)
    }

    @Test
    fun aFrameFromAnOlderServerWithoutACount_fallsBackToPlusOne() {
        val result = message(reactions = mapOf(thumbs to 1)).applyReactionFrame(reactionFrame(them, "add", count = null), me)

        assertEquals(mapOf(thumbs to 2), result.reactions)
    }

    @Test
    fun aFrameForAnotherMessage_isIgnored() {
        val start = message()

        val result = start.applyReactionFrame(reactionFrame(them, "add", count = 5).copy(messageId = "other"), me)

        assertSame(start, result)
    }

    @Test
    fun togglingAnEmojiAlreadyMine_removesItInsteadOfAddingADuplicate() {
        val toggle = message(reactions = mapOf(thumbs to 3), myReactions = setOf(thumbs)).toggleReaction(thumbs)

        assertFalse(toggle.add)
        assertEquals(mapOf(thumbs to 2), toggle.optimistic.reactions)
        assertEquals(emptySet(), toggle.optimistic.myReactions)
    }

    @Test
    fun togglingANewEmoji_addsItOptimistically() {
        val toggle = message().toggleReaction(thumbs)

        assertTrue(toggle.add)
        assertEquals(mapOf(thumbs to 1), toggle.optimistic.reactions)
        assertEquals(setOf(thumbs), toggle.optimistic.myReactions)
    }

    @Test
    fun theServersAnswer_replacesWhateverWasGuessed() {
        val guessed = message().toggleReaction(thumbs).optimistic

        val reconciled = guessed.withServerReactions(
            MessageReactionsResponse(messageId = "m1", reactions = mapOf(thumbs to 4), myReactions = setOf(thumbs))
        )

        assertEquals(mapOf(thumbs to 4), reconciled.reactions)
    }

    @Test
    fun theServersAnswerForAnotherMessage_isIgnored() {
        val start = message()

        assertSame(start, start.withServerReactions(MessageReactionsResponse("other", mapOf(thumbs to 1), emptySet())))
    }

    // ─── Polls ───────────────────────────────────────────────────

    private fun pollFrame(voter: String, option: Int, counts: List<Int>) = WsMessage.PollUpdated(
        messageId = "m1",
        conversationId = "c1",
        voterId = voter,
        optionIndex = option,
        counts = counts,
        totalVotes = counts.sum(),
    )

    @Test
    fun someoneElsesVote_updatesTheCountsAndKeepsMyChoice() {
        val start = message(poll = PollTally(counts = listOf(1, 0), totalVotes = 1, myVote = 0))

        val result = start.applyPollUpdate(pollFrame(them, 1, listOf(1, 1)), me)

        assertEquals(PollTally(counts = listOf(1, 1), totalVotes = 2, myVote = 0), result.poll)
    }

    @Test
    fun myVoteFromAnotherDevice_movesMyChoice() {
        val start = message(poll = PollTally(counts = listOf(1, 0), totalVotes = 1, myVote = 0))

        val result = start.applyPollUpdate(pollFrame(me, 1, listOf(0, 1)), me)

        assertEquals(1, result.poll?.myVote)
    }

    @Test
    fun theSamePollFrameTwice_isIdempotent() {
        val frame = pollFrame(them, 1, listOf(0, 1))

        val once = message().applyPollUpdate(frame, me)

        assertEquals(once, once.applyPollUpdate(frame, me))
    }

    @Test
    fun anOptimisticChangeOfMind_movesOneVoteWithoutChangingTheTotal() {
        val start = message(poll = PollTally(counts = listOf(2, 1), totalVotes = 3, myVote = 0))

        val result = start.withOptimisticVote(optionIndex = 1, optionCount = 2)

        assertEquals(PollTally(counts = listOf(1, 2), totalVotes = 3, myVote = 1), result.poll)
    }

    @Test
    fun anOptimisticFirstVote_onAPollWithNoTallyYet_startsFromZero() {
        val result = message().withOptimisticVote(optionIndex = 2, optionCount = 3)

        assertEquals(PollTally(counts = listOf(0, 0, 1), totalVotes = 1, myVote = 2), result.poll)
    }

    @Test
    fun votingForTheOptionAlreadyChosen_changesNothing() {
        val start = message(poll = PollTally(counts = listOf(1, 0), totalVotes = 1, myVote = 0))

        assertSame(start, start.withOptimisticVote(optionIndex = 0, optionCount = 2))
    }

    @Test
    fun aPollResultResponse_becomesATallyInOptionOrder() {
        val response = PollResultResponse(
            messageId = "m1",
            votes = listOf(PollOptionResult(1, "b", 4), PollOptionResult(0, "a", 2)),
            totalVotes = 6,
            myVote = 1,
        )

        assertEquals(PollTally(counts = listOf(2, 4), totalVotes = 6, myVote = 1), response.toTally())
    }

    // ─── Stars ───────────────────────────────────────────────────

    @Test
    fun starringAndRollingBack_restoresTheOriginal() {
        val start = message()

        assertEquals(start, start.withStarred(true).withStarred(false))
        assertTrue(start.withStarred(true).isStarred)
    }
}

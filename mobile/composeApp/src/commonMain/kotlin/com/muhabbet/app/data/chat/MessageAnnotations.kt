package com.muhabbet.app.data.chat

import com.muhabbet.shared.dto.MessageReactionsResponse
import com.muhabbet.shared.dto.PollResultResponse
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.PollTally
import com.muhabbet.shared.protocol.WsMessage

/*
 * Pure transitions for what a chat shows *around* a message — reactions, a poll's tally, a star —
 * so the coming ChatStore applies events through tested functions instead of open-coding
 * arithmetic in a composable (#775, #781, #783).
 *
 * Every function here is total and idempotent where the input is a server fact: the socket does not
 * deduplicate reaction or poll frames, and the same fact arriving twice (a reconnect, the echo of
 * this device's own optimistic change, another device of the same user) must leave the same state
 * as arriving once. That is why they *set* counts from the server rather than add to them.
 */

// ─── Reactions (#783) ──────────────────────────────────────────

/** What a reaction tap should do, decided from the message as currently shown. */
data class ReactionToggle(
    /** The message with the change already applied, to show before the server answers. */
    val optimistic: Message,
    /** True to POST the reaction, false to DELETE it. */
    val add: Boolean
)

/**
 * Decides add-or-remove from the viewer's own reactions and applies it optimistically.
 *
 * Both the reaction bar and the quick-reaction strip must come through here. The quick strip used
 * to POST unconditionally, so tapping an emoji the user had already chosen asked the server to add
 * it a second time — the duplicate this exists to make impossible.
 */
fun Message.toggleReaction(emoji: String): ReactionToggle {
    val add = emoji !in myReactions
    val current = reactions[emoji] ?: 0
    val count = if (add) current + 1 else (current - 1).coerceAtLeast(0)
    return ReactionToggle(
        optimistic = copy(
            reactions = reactions.withCount(emoji, count),
            myReactions = if (add) myReactions + emoji else myReactions - emoji
        ),
        add = add
    )
}

/** Replaces the guess with the server's answer to the write — whatever was guessed. */
fun Message.withServerReactions(response: MessageReactionsResponse): Message =
    if (response.messageId != id) this else copy(reactions = response.reactions, myReactions = response.myReactions)

/**
 * Applies a `message.reaction` frame. With [WsMessage.MessageReaction.count] present the count is
 * set, not adjusted, so a duplicate frame changes nothing. The viewer's own set only changes for a
 * frame about the viewer, and as a set operation, which is idempotent too.
 *
 * A frame from a server older than the count field falls back to ±1 — the old behaviour, kept only
 * so a client newer than its server is no worse than before.
 */
fun Message.applyReactionFrame(frame: WsMessage.MessageReaction, currentUserId: String): Message {
    if (frame.messageId != id) return this
    val adding = frame.action == ACTION_ADD
    val count = frame.count ?: run {
        val current = reactions[frame.emoji] ?: 0
        if (adding) current + 1 else current - 1
    }
    val mine = when {
        frame.userId != currentUserId -> myReactions
        adding -> myReactions + frame.emoji
        else -> myReactions - frame.emoji
    }
    return copy(reactions = reactions.withCount(frame.emoji, count), myReactions = mine)
}

private const val ACTION_ADD = "add"

private fun Map<String, Int>.withCount(emoji: String, count: Int): Map<String, Int> =
    if (count <= 0) this - emoji else this + (emoji to count)

// ─── Polls (#781) ──────────────────────────────────────────────

fun PollResultResponse.toTally(): PollTally = PollTally(
    counts = votes.sortedBy { it.index }.map { it.count },
    totalVotes = totalVotes,
    myVote = myVote
)

/**
 * Applies a `poll.updated` frame. The counts are the server's and replace ours; the viewer's own
 * choice only changes when the frame is about the viewer's vote (made on another of their devices,
 * or the echo of this one), because nobody else's vote can change it.
 */
fun Message.applyPollUpdate(frame: WsMessage.PollUpdated, currentUserId: String): Message {
    if (frame.messageId != id) return this
    val myVote = if (frame.voterId == currentUserId) frame.optionIndex else poll?.myVote
    return copy(poll = PollTally(counts = frame.counts, totalVotes = frame.totalVotes, myVote = myVote))
}

/**
 * The vote as it will look once the server agrees: the viewer's previous option loses one, the new
 * one gains one, and a vote for the option already chosen changes nothing. [optionCount] is the
 * number of options in the poll body, for a poll whose tally has not arrived yet.
 */
fun Message.withOptimisticVote(optionIndex: Int, optionCount: Int): Message {
    if (optionIndex !in 0 until optionCount) return this
    val current = poll ?: PollTally(counts = List(optionCount) { 0 }, totalVotes = 0, myVote = null)
    if (current.myVote == optionIndex) return this
    val counts = List(optionCount) { index -> current.counts.getOrElse(index) { 0 } }.toMutableList()
    val previous = current.myVote
    if (previous != null && previous in counts.indices) {
        counts[previous] = (counts[previous] - 1).coerceAtLeast(0)
    }
    counts[optionIndex] += 1
    val total = if (previous == null) current.totalVotes + 1 else current.totalVotes
    return copy(poll = PollTally(counts = counts, totalVotes = total, myVote = optionIndex))
}

// ─── Stars (#775) ──────────────────────────────────────────────

/** The star as shown before the server confirms; roll back by applying the opposite value. */
fun Message.withStarred(starred: Boolean): Message = copy(isStarred = starred)

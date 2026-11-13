package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.PollVote
import com.muhabbet.messaging.domain.port.`in`.PollCounts
import java.util.UUID

/**
 * The one place a list of votes becomes a tally (#781), shared by the vote and results endpoints,
 * the history page and the `poll.updated` frame so the four cannot count differently.
 *
 * A vote whose index is outside [optionCount] is not counted anywhere — not in its option and not
 * in the total — so the bars always sum to the total the bubble prints.
 */
internal fun tallyPoll(optionCount: Int, votes: List<PollVote>, viewerId: UUID?): PollCounts {
    val counted = votes.filter { it.optionIndex in 0 until optionCount }
    val perOption = counted.groupingBy { it.optionIndex }.eachCount()
    return PollCounts(
        counts = List(optionCount) { index -> perOption[index] ?: 0 },
        totalVotes = counted.size,
        myVote = viewerId?.let { id -> counted.firstOrNull { it.userId == id }?.optionIndex }
    )
}

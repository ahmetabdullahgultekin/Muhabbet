package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.PollVote
import java.util.UUID

interface PollVoteRepository {
    /**
     * Records [vote] as its voter's one vote on the poll, replacing any earlier choice, in a single
     * atomic statement (#781, #336). The delete-then-insert it replaces let two concurrent votes from
     * one user both delete and both insert, and the loser surfaced as a 500.
     */
    fun upsert(vote: PollVote)
    fun findByMessageId(messageId: UUID): List<PollVote>

    /** Every vote on any of [messageIds], in one query — for a history page (#781). */
    fun findByMessageIds(messageIds: Collection<UUID>): List<PollVote>
}

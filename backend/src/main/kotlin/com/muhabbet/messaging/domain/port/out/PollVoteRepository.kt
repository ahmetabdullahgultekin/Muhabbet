package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.PollVote
import java.util.UUID

interface PollVoteRepository {
    fun save(vote: PollVote): PollVote
    fun findByMessageId(messageId: UUID): List<PollVote>
    fun deleteByMessageIdAndUserId(messageId: UUID, userId: UUID)
}

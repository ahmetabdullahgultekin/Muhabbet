package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataPollVoteRepository
import com.muhabbet.messaging.domain.model.PollVote
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PollVotePersistenceAdapter(
    private val pollVoteRepo: SpringDataPollVoteRepository
) : PollVoteRepository {

    override fun upsert(vote: PollVote) {
        pollVoteRepo.upsert(
            id = vote.id,
            messageId = vote.messageId,
            userId = vote.userId,
            optionIndex = vote.optionIndex,
            votedAt = vote.votedAt
        )
    }

    override fun findByMessageId(messageId: UUID): List<PollVote> =
        pollVoteRepo.findByMessageId(messageId).map { it.toDomain() }

    override fun findByMessageIds(messageIds: Collection<UUID>): List<PollVote> =
        if (messageIds.isEmpty()) emptyList() else pollVoteRepo.findByMessageIdIn(messageIds).map { it.toDomain() }
}

package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.PollVoteJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataPollVoteRepository
import com.muhabbet.messaging.domain.model.PollVote
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PollVotePersistenceAdapter(
    private val pollVoteRepo: SpringDataPollVoteRepository
) : PollVoteRepository {

    override fun save(vote: PollVote): PollVote =
        pollVoteRepo.save(PollVoteJpaEntity.fromDomain(vote)).toDomain()

    override fun findByMessageId(messageId: UUID): List<PollVote> =
        pollVoteRepo.findByMessageId(messageId).map { it.toDomain() }

    override fun deleteByMessageIdAndUserId(messageId: UUID, userId: UUID) {
        pollVoteRepo.deleteByMessageIdAndUserId(messageId, userId)
    }
}

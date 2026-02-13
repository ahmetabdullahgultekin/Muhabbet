package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.PollVote
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "poll_votes")
class PollVoteJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "message_id", nullable = false)
    val messageId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "option_index", nullable = false)
    val optionIndex: Int,

    @Column(name = "voted_at", nullable = false)
    val votedAt: Instant = Instant.now()
) {
    fun toDomain(): PollVote = PollVote(
        id = id, messageId = messageId, userId = userId,
        optionIndex = optionIndex, votedAt = votedAt
    )

    companion object {
        fun fromDomain(v: PollVote): PollVoteJpaEntity = PollVoteJpaEntity(
            id = v.id, messageId = v.messageId, userId = v.userId,
            optionIndex = v.optionIndex, votedAt = v.votedAt
        )
    }
}

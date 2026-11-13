package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.PollVoteJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataPollVoteRepository : JpaRepository<PollVoteJpaEntity, UUID> {

    fun findByMessageId(messageId: UUID): List<PollVoteJpaEntity>

    fun findByMessageIdIn(messageIds: Collection<UUID>): List<PollVoteJpaEntity>

    fun findByMessageIdAndUserId(messageId: UUID, userId: UUID): PollVoteJpaEntity?

    @Modifying
    @Query(
        value = """
            INSERT INTO poll_votes (id, message_id, user_id, option_index, voted_at)
            VALUES (:id, :messageId, :userId, :optionIndex, :votedAt)
            ON CONFLICT (message_id, user_id)
            DO UPDATE SET option_index = EXCLUDED.option_index, voted_at = EXCLUDED.voted_at
        """,
        nativeQuery = true
    )
    fun upsert(id: UUID, messageId: UUID, userId: UUID, optionIndex: Int, votedAt: Instant): Int
}

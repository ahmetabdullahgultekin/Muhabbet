package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.PollVoteJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataPollVoteRepository : JpaRepository<PollVoteJpaEntity, UUID> {

    fun findByMessageId(messageId: UUID): List<PollVoteJpaEntity>

    fun findByMessageIdAndUserId(messageId: UUID, userId: UUID): PollVoteJpaEntity?

    @Modifying
    @Query("DELETE FROM PollVoteJpaEntity v WHERE v.messageId = :messageId AND v.userId = :userId")
    fun deleteByMessageIdAndUserId(messageId: UUID, userId: UUID)
}

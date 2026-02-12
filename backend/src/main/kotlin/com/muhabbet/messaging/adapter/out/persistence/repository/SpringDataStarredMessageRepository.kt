package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.StarredMessageJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataStarredMessageRepository : JpaRepository<StarredMessageJpaEntity, UUID> {
    fun existsByUserIdAndMessageId(userId: UUID, messageId: UUID): Boolean
    fun deleteByUserIdAndMessageId(userId: UUID, messageId: UUID)

    @Query("SELECT s.messageId FROM StarredMessageJpaEntity s WHERE s.userId = :userId AND s.messageId IN :messageIds")
    fun findStarredMessageIds(userId: UUID, messageIds: List<UUID>): List<UUID>

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): List<StarredMessageJpaEntity>
}

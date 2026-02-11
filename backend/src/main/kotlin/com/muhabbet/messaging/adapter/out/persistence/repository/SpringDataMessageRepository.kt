package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataMessageRepository : JpaRepository<MessageJpaEntity, UUID> {

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND (:before IS NULL OR m.serverTimestamp < :before)
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun findByConversationIdBefore(conversationId: UUID, before: Instant?, pageable: Pageable): List<MessageJpaEntity>

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
        ORDER BY m.serverTimestamp DESC
        LIMIT 1
        """
    )
    fun findLastByConversationId(conversationId: UUID): MessageJpaEntity?
}

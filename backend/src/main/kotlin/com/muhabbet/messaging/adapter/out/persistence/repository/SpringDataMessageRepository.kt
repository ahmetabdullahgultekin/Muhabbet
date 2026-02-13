package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataMessageRepository : JpaRepository<MessageJpaEntity, UUID> {

    @Modifying
    @Query("UPDATE MessageJpaEntity m SET m.isDeleted = true, m.deletedAt = CURRENT_TIMESTAMP WHERE m.id = :messageId")
    fun softDelete(messageId: UUID)

    @Modifying
    @Query("UPDATE MessageJpaEntity m SET m.content = :newContent, m.editedAt = :editedAt WHERE m.id = :messageId")
    fun updateContent(messageId: UUID, newContent: String, editedAt: java.time.Instant)

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND m.serverTimestamp < :before
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun findByConversationIdBefore(conversationId: UUID, before: Instant, pageable: Pageable): List<MessageJpaEntity>

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun findByConversationIdLatest(conversationId: UUID, pageable: Pageable): List<MessageJpaEntity>

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

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND LOWER(m.content) LIKE :query
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun searchInConversation(conversationId: UUID, query: String, pageable: Pageable): List<MessageJpaEntity>

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.isDeleted = false
          AND LOWER(m.content) LIKE :query
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun searchGlobal(query: String, pageable: Pageable): List<MessageJpaEntity>

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.expiresAt IS NOT NULL
          AND m.expiresAt <= :now
          AND m.isDeleted = false
        """
    )
    fun findExpiredMessages(now: Instant): List<MessageJpaEntity>

    @Query(
        """
        SELECT COUNT(m) FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND m.mediaUrl IS NOT NULL
        """
    )
    fun countMediaInConversation(conversationId: UUID): Int

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND m.contentType IN (
            com.muhabbet.messaging.domain.model.ContentType.IMAGE,
            com.muhabbet.messaging.domain.model.ContentType.VIDEO,
            com.muhabbet.messaging.domain.model.ContentType.DOCUMENT,
            com.muhabbet.messaging.domain.model.ContentType.VOICE
          )
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun findMediaByConversationId(conversationId: UUID, pageable: Pageable): List<MessageJpaEntity>
}

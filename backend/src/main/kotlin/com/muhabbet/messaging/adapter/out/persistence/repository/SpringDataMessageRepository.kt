package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import com.muhabbet.messaging.domain.model.ContentType
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

    // Batch fetch last message per conversation (replaces N individual queries)
    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.isDeleted = false
          AND m.conversationId IN :conversationIds
          AND m.serverTimestamp = (
            SELECT MAX(m2.serverTimestamp) FROM MessageJpaEntity m2
            WHERE m2.conversationId = m.conversationId AND m2.isDeleted = false
          )
        """
    )
    fun findLastMessagesByConversationIds(conversationIds: List<UUID>): List<MessageJpaEntity>

    // Membership-scoped search: only returns messages from a conversation the requesting user
    // is a member of. The JOIN to conversation_members filtered by :userId closes the IDOR.
    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        JOIN ConversationMemberJpaEntity cm
          ON cm.conversationId = m.conversationId
        WHERE m.conversationId = :conversationId
          AND cm.userId = :userId
          AND m.isDeleted = false
          AND LOWER(m.content) LIKE :query
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun searchInConversation(conversationId: UUID, userId: UUID, query: String, pageable: Pageable): List<MessageJpaEntity>

    // Membership-scoped global search: only the requesting user's own conversations are searched.
    @Query(
        """
        SELECT DISTINCT m FROM MessageJpaEntity m
        JOIN ConversationMemberJpaEntity cm
          ON cm.conversationId = m.conversationId
        WHERE cm.userId = :userId
          AND m.isDeleted = false
          AND LOWER(m.content) LIKE :query
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun searchGlobal(userId: UUID, query: String, pageable: Pageable): List<MessageJpaEntity>

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.expiresAt IS NOT NULL
          AND m.expiresAt <= :now
          AND m.isDeleted = false
        ORDER BY m.expiresAt ASC
        """
    )
    fun findExpiredMessages(now: Instant, pageable: Pageable): List<MessageJpaEntity>

    /** Returns the rows updated. One statement for the whole sweep — see `MessageRepository`. */
    @Modifying
    @Query("UPDATE MessageJpaEntity m SET m.isDeleted = true, m.deletedAt = :deletedAt WHERE m.id IN :messageIds AND m.isDeleted = false")
    fun softDeleteExpired(messageIds: List<UUID>, deletedAt: Instant): Int

    @Query(
        """
        SELECT COUNT(m) FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND m.mediaUrl IS NOT NULL
        """
    )
    fun countMediaInConversation(conversationId: UUID): Int

    /**
     * Backs the shared-media grid, which is why view-once messages are excluded rather than merely
     * stripped.
     *
     * They used to be listed like any other photo, with a working presigned URL, so the whole seal
     * could be walked around by leaving the chat and opening the gallery — for a message the
     * recipient had already burned as much as for one they had never opened. Stripping the URL alone
     * would have swapped that for a grid of broken tiles: a view-once photo has nothing a gallery can
     * legitimately show.
     */
    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND m.viewOnce = false
          AND m.contentType IN :contentTypes
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun findMediaByConversationId(conversationId: UUID, contentTypes: List<ContentType>, pageable: Pageable): List<MessageJpaEntity>

    @Query(
        """
        SELECT DISTINCT m FROM MessageJpaEntity m
        JOIN ConversationMemberJpaEntity cm
          ON m.conversationId = cm.conversationId
        WHERE cm.userId = :userId
          AND m.isDeleted = false
          AND m.serverTimestamp > :since
        ORDER BY m.serverTimestamp ASC
        """
    )
    fun findMessagesSince(userId: UUID, since: Instant, pageable: Pageable): List<MessageJpaEntity>

    /** Returns the rows updated — 0 means someone else burned it first. See `MessageRepository`. */
    @Modifying
    @Query("UPDATE MessageJpaEntity m SET m.viewedAt = :viewedAt, m.viewedBy = :viewedBy WHERE m.id = :messageId AND m.viewOnce = true AND m.viewedAt IS NULL")
    fun markViewOnceViewed(messageId: UUID, viewedBy: UUID, viewedAt: Instant): Int

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.isScheduled = true
          AND m.scheduledAt IS NOT NULL
          AND m.scheduledAt <= :now
          AND m.isDeleted = false
        ORDER BY m.scheduledAt ASC
        """
    )
    fun findScheduledMessagesReadyToSend(now: Instant, pageable: Pageable): List<MessageJpaEntity>

    @Modifying
    @Query("UPDATE MessageJpaEntity m SET m.isScheduled = false WHERE m.id = :messageId")
    fun markScheduledAsDelivered(messageId: UUID)
}

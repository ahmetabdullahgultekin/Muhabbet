package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import com.muhabbet.messaging.domain.model.ContentType
import java.util.UUID

/**
 * **Every read that can return another user's message carries the scheduled-row predicate**
 * `(m.isScheduled = false OR m.senderId = :viewerId)` (#746). A scheduled message is a real row
 * from the moment it is written, so a query without the predicate hands it to the recipient before
 * it is due. Add the predicate to any new read here; the only queries without it are the ones that
 * look a row up by id for its own sender or for the scheduler.
 */
interface SpringDataMessageRepository : JpaRepository<MessageJpaEntity, UUID> {

    @Modifying
    @Query("UPDATE MessageJpaEntity m SET m.isDeleted = true, m.deletedAt = CURRENT_TIMESTAMP WHERE m.id = :messageId")
    fun softDelete(messageId: UUID)

    /** A delete that is an event in the conversation's order: it stamps the allocated [changeSeq]. */
    @Modifying
    @Query(
        "UPDATE MessageJpaEntity m SET m.isDeleted = true, m.deletedAt = CURRENT_TIMESTAMP, " +
            "m.changeSeq = :changeSeq WHERE m.id = :messageId"
    )
    fun softDeleteWithChange(messageId: UUID, changeSeq: Long)

    @Modifying
    @Query(
        "UPDATE MessageJpaEntity m SET m.content = :newContent, m.editedAt = :editedAt, " +
            "m.changeSeq = :changeSeq WHERE m.id = :messageId"
    )
    fun updateContent(messageId: UUID, newContent: String, editedAt: java.time.Instant, changeSeq: Long)

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND (m.isScheduled = false OR m.senderId = :viewerId)
          AND m.serverTimestamp < :before
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun findByConversationIdBefore(
        conversationId: UUID,
        viewerId: UUID,
        before: Instant,
        pageable: Pageable
    ): List<MessageJpaEntity>

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND (m.isScheduled = false OR m.senderId = :viewerId)
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun findByConversationIdLatest(conversationId: UUID, viewerId: UUID, pageable: Pageable): List<MessageJpaEntity>

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND (m.isScheduled = false OR m.senderId = :viewerId)
        ORDER BY m.serverTimestamp DESC
        LIMIT 1
        """
    )
    fun findLastByConversationId(conversationId: UUID, viewerId: UUID): MessageJpaEntity?

    // Batch fetch last message per conversation (replaces N individual queries)
    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.isDeleted = false
          AND m.conversationId IN :conversationIds
          AND (m.isScheduled = false OR m.senderId = :viewerId)
          AND m.serverTimestamp = (
            SELECT MAX(m2.serverTimestamp) FROM MessageJpaEntity m2
            WHERE m2.conversationId = m.conversationId
              AND m2.isDeleted = false
              AND (m2.isScheduled = false OR m2.senderId = :viewerId)
          )
        """
    )
    fun findLastMessagesByConversationIds(conversationIds: List<UUID>, viewerId: UUID): List<MessageJpaEntity>

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
          AND (m.isScheduled = false OR m.senderId = :userId)
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
          AND (m.isScheduled = false OR m.senderId = :userId)
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
          AND (m.isScheduled = false OR m.senderId = :viewerId)
          AND m.mediaUrl IS NOT NULL
        """
    )
    fun countMediaInConversation(conversationId: UUID, viewerId: UUID): Int

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
          AND (m.isScheduled = false OR m.senderId = :viewerId)
          AND m.contentType IN :contentTypes
        ORDER BY m.serverTimestamp DESC
        """
    )
    fun findMediaByConversationId(
        conversationId: UUID,
        viewerId: UUID,
        contentTypes: List<ContentType>,
        pageable: Pageable
    ): List<MessageJpaEntity>

    @Query(
        """
        SELECT DISTINCT m FROM MessageJpaEntity m
        JOIN ConversationMemberJpaEntity cm
          ON m.conversationId = cm.conversationId
        WHERE cm.userId = :userId
          AND m.isDeleted = false
          AND (m.isScheduled = false OR m.senderId = :userId)
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

    /**
     * Releases a scheduled message: it takes its [seq] now, not when it was written (ADR 0014), and
     * its `serverTimestamp` becomes the release time. The second matters as much as the first —
     * the row was hidden from recipients until this moment, so a recipient's `/messages/since`
     * watermark is already past its write time, and a release that kept the old timestamp would be
     * missed by background sync for good.
     */
    @Modifying
    @Query(
        "UPDATE MessageJpaEntity m SET m.isScheduled = false, m.seq = :seq, m.changeSeq = :seq, " +
            "m.serverTimestamp = :releasedAt WHERE m.id = :messageId AND m.isScheduled = true"
    )
    fun markScheduledAsDelivered(messageId: UUID, seq: Long, releasedAt: Instant): Int
}

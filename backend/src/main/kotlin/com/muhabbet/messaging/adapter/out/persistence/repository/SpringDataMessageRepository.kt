package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.ConversationType
import java.util.UUID

interface SpringDataMessageRepository : JpaRepository<MessageJpaEntity, UUID> {

    @Modifying
    @Query("UPDATE MessageJpaEntity m SET m.isDeleted = true, m.deletedAt = CURRENT_TIMESTAMP WHERE m.id = :messageId")
    fun softDelete(messageId: UUID)

    @Modifying
    @Query("UPDATE MessageJpaEntity m SET m.content = :newContent, m.editedAt = :editedAt WHERE m.id = :messageId")
    fun updateContent(messageId: UUID, newContent: String, editedAt: java.time.Instant)

    // ---- History (#750, #874, #876) -------------------------------------------------------------
    //
    // Keyset over (serverTimestamp, id): the row comparison is a total order, so a page boundary
    // inside a run of equal timestamps can neither skip nor repeat a row, which the old
    // `serverTimestamp < :before` did. It is also a single index condition on
    // idx_messages_conv_history (conversation_id, server_timestamp DESC, id DESC) — V28 — so a deep
    // page costs the same as the first one. `visibleFrom` is the member's HistoryWindow, EPOCH when
    // unbounded. Scheduled messages that have not gone out yet are nobody's history: they used to
    // appear in the recipient's chat the moment they were scheduled.

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND m.isScheduled = false
          AND m.serverTimestamp >= :visibleFrom
        ORDER BY m.serverTimestamp DESC, m.id DESC
        """
    )
    fun findHistoryLatest(conversationId: UUID, visibleFrom: Instant, pageable: Pageable): List<MessageJpaEntity>

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND m.isScheduled = false
          AND m.serverTimestamp >= :visibleFrom
          AND (m.serverTimestamp, m.id) < (:cursorTimestamp, :cursorId)
        ORDER BY m.serverTimestamp DESC, m.id DESC
        """
    )
    fun findHistoryBefore(
        conversationId: UUID,
        visibleFrom: Instant,
        cursorTimestamp: Instant,
        cursorId: UUID,
        pageable: Pageable
    ): List<MessageJpaEntity>

    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.conversationId = :conversationId
          AND m.isDeleted = false
          AND m.isScheduled = false
          AND m.serverTimestamp >= :visibleFrom
          AND (m.serverTimestamp, m.id) > (:cursorTimestamp, :cursorId)
        ORDER BY m.serverTimestamp ASC, m.id ASC
        """
    )
    fun findHistoryAfter(
        conversationId: UUID,
        visibleFrom: Instant,
        cursorTimestamp: Instant,
        cursorId: UUID,
        pageable: Pageable
    ): List<MessageJpaEntity>

    /**
     * The newest message [userId] may read in each conversation: the inbox preview.
     *
     * The membership join and the `type <> GROUP OR serverTimestamp >= joinedAt` clause are
     * `HistoryWindow.of` written in JPQL, because a batch across conversations cannot be handed one
     * window. Filtering only the outer row is enough: if the conversation's newest message is outside
     * the window, every older one is too, and the conversation correctly has no preview.
     */
    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        JOIN ConversationMemberJpaEntity cm
          ON cm.conversationId = m.conversationId AND cm.userId = :userId
        JOIN ConversationJpaEntity c
          ON c.id = m.conversationId
        WHERE m.isDeleted = false
          AND m.isScheduled = false
          AND m.conversationId IN :conversationIds
          AND (c.type <> :groupType OR m.serverTimestamp >= cm.joinedAt)
          AND m.serverTimestamp = (
            SELECT MAX(m2.serverTimestamp) FROM MessageJpaEntity m2
            WHERE m2.conversationId = m.conversationId
              AND m2.isDeleted = false
              AND m2.isScheduled = false
          )
        """
    )
    fun findLastMessagesByConversationIds(
        conversationIds: List<UUID>,
        userId: UUID,
        groupType: ConversationType
    ): List<MessageJpaEntity>

    // Membership-scoped search: only returns messages from a conversation the requesting user
    // is a member of. The JOIN to conversation_members filtered by :userId closes the IDOR; the
    // service has already resolved the member's HistoryWindow into :visibleFrom.
    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        JOIN ConversationMemberJpaEntity cm
          ON cm.conversationId = m.conversationId
        WHERE m.conversationId = :conversationId
          AND cm.userId = :userId
          AND m.isDeleted = false
          AND m.isScheduled = false
          AND m.serverTimestamp >= :visibleFrom
          AND LOWER(m.content) LIKE :query
        ORDER BY m.serverTimestamp DESC, m.id DESC
        """
    )
    fun searchInConversation(
        conversationId: UUID,
        userId: UUID,
        visibleFrom: Instant,
        query: String,
        pageable: Pageable
    ): List<MessageJpaEntity>

    // Membership-scoped global search: only the requesting user's own conversations are searched,
    // and within each only the part their membership opens (HistoryWindow.of, per row — #874).
    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.isDeleted = false
          AND m.isScheduled = false
          AND LOWER(m.content) LIKE :query
          AND EXISTS (
            SELECT 1 FROM ConversationMemberJpaEntity cm
            JOIN ConversationJpaEntity c ON c.id = cm.conversationId
            WHERE cm.conversationId = m.conversationId
              AND cm.userId = :userId
              AND (c.type <> :groupType OR m.serverTimestamp >= cm.joinedAt)
          )
        ORDER BY m.serverTimestamp DESC, m.id DESC
        """
    )
    fun searchGlobal(userId: UUID, query: String, groupType: ConversationType, pageable: Pageable): List<MessageJpaEntity>

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
          AND m.isScheduled = false
          AND m.viewOnce = false
          AND m.contentType IN :contentTypes
          AND m.serverTimestamp >= :visibleFrom
        ORDER BY m.serverTimestamp DESC, m.id DESC
        """
    )
    fun findMediaByConversationId(
        conversationId: UUID,
        contentTypes: List<ContentType>,
        visibleFrom: Instant,
        pageable: Pageable
    ): List<MessageJpaEntity>

    /**
     * Background sync's catch-up read. `EXISTS` rather than the `JOIN … DISTINCT` it replaces: a user
     * has one membership row per conversation, so the join never produced duplicates and the
     * DISTINCT only forced a sort over every matching row. The window clause is `HistoryWindow.of`
     * in JPQL, as in [findLastMessagesByConversationIds].
     */
    @Query(
        """
        SELECT m FROM MessageJpaEntity m
        WHERE m.isDeleted = false
          AND m.isScheduled = false
          AND m.serverTimestamp > :since
          AND EXISTS (
            SELECT 1 FROM ConversationMemberJpaEntity cm
            JOIN ConversationJpaEntity c ON c.id = cm.conversationId
            WHERE cm.conversationId = m.conversationId
              AND cm.userId = :userId
              AND (c.type <> :groupType OR m.serverTimestamp >= cm.joinedAt)
          )
        ORDER BY m.serverTimestamp ASC, m.id ASC
        """
    )
    fun findMessagesSince(userId: UUID, since: Instant, groupType: ConversationType, pageable: Pageable): List<MessageJpaEntity>

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

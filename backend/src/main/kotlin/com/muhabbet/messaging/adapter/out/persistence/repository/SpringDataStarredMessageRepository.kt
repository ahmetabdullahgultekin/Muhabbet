package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.StarredMessageJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataStarredMessageRepository : JpaRepository<StarredMessageJpaEntity, UUID> {

    /**
     * One statement, so the `UNIQUE (user_id, message_id)` constraint (V5) is the arbiter rather
     * than an exists-then-insert pair that two concurrent requests could both pass (#906). The id
     * and `created_at` come from the column defaults.
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO starred_messages (user_id, message_id)
        VALUES (:userId, :messageId)
        ON CONFLICT (user_id, message_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(userId: UUID, messageId: UUID): Int

    @Modifying
    @Query("DELETE FROM StarredMessageJpaEntity s WHERE s.userId = :userId AND s.messageId = :messageId")
    fun deleteByUserIdAndMessageId(userId: UUID, messageId: UUID): Int

    /**
     * The newest page of a user's bookmarks that they can still read. Each row is
     * `[StarredMessageJpaEntity, MessageJpaEntity]`.
     *
     * The join to `conversation_members` on the *caller* is the authorization: a bookmark whose
     * conversation the caller has left simply does not come back. The state predicates are the
     * ones history and starring agree on — no deleted, view-once or still-scheduled message.
     */
    @Query(
        """
        SELECT s, m FROM StarredMessageJpaEntity s, MessageJpaEntity m, ConversationMemberJpaEntity cm
        WHERE s.userId = :userId
          AND m.id = s.messageId
          AND cm.conversationId = m.conversationId
          AND cm.userId = :userId
          AND m.isDeleted = false
          AND m.viewOnce = false
          AND m.isScheduled = false
        ORDER BY s.createdAt DESC, s.id DESC
        """
    )
    fun findVisibleLatest(userId: UUID, pageable: Pageable): List<Array<Any>>

    /** As [findVisibleLatest], strictly after the keyset position `(beforeAt, beforeId)`. */
    @Query(
        """
        SELECT s, m FROM StarredMessageJpaEntity s, MessageJpaEntity m, ConversationMemberJpaEntity cm
        WHERE s.userId = :userId
          AND m.id = s.messageId
          AND cm.conversationId = m.conversationId
          AND cm.userId = :userId
          AND m.isDeleted = false
          AND m.viewOnce = false
          AND m.isScheduled = false
          AND (s.createdAt < :beforeAt OR (s.createdAt = :beforeAt AND s.id < :beforeId))
        ORDER BY s.createdAt DESC, s.id DESC
        """
    )
    fun findVisibleBefore(userId: UUID, beforeAt: Instant, beforeId: UUID, pageable: Pageable): List<Array<Any>>
}

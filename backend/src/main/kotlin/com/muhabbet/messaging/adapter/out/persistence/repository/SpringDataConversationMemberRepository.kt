package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.ConversationMemberId
import com.muhabbet.messaging.adapter.out.persistence.entity.ConversationMemberJpaEntity
import com.muhabbet.messaging.domain.model.ConversationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataConversationMemberRepository : JpaRepository<ConversationMemberJpaEntity, ConversationMemberId> {
    fun findByConversationId(conversationId: UUID): List<ConversationMemberJpaEntity>
    fun countByConversationId(conversationId: UUID): Long
    fun findByConversationIdIn(conversationIds: List<UUID>): List<ConversationMemberJpaEntity>

    // Batch member counts (replaces N countByConversationId calls)
    @Query(
        """
        SELECT m.conversationId, COUNT(m) FROM ConversationMemberJpaEntity m
        WHERE m.conversationId IN :conversationIds
        GROUP BY m.conversationId
        """
    )
    fun countByConversationIds(conversationIds: List<UUID>): List<Array<Any>>

    fun findByUserId(userId: UUID): List<ConversationMemberJpaEntity>
    fun findByConversationIdAndUserId(conversationId: UUID, userId: UUID): ConversationMemberJpaEntity?

    /** One user against many conversations — an existence check, so no rows are materialised. */
    fun existsByUserIdAndConversationIdIn(userId: UUID, conversationIds: List<UUID>): Boolean

    /**
     * Everyone [userId] shares a conversation of one of [types] with, in a conversation where
     * [userId] has taken part: created it, or sent at least one message in it.
     */
    @Query(
        """
        SELECT DISTINCT other.userId FROM ConversationMemberJpaEntity me, ConversationMemberJpaEntity other, ConversationJpaEntity c
        WHERE me.userId = :userId
          AND other.conversationId = me.conversationId
          AND other.userId <> :userId
          AND c.id = me.conversationId
          AND c.type IN :types
          AND (c.createdBy = :userId OR EXISTS (
                SELECT 1 FROM MessageJpaEntity msg WHERE msg.conversationId = c.id AND msg.senderId = :userId))
        """
    )
    fun findPeersEngagedBy(userId: UUID, types: List<ConversationType>): Set<UUID>

    /**
     * Everyone who shares a conversation of one of [types] with [userId] and has taken part in it:
     * created it, or sent at least one message in it. The mirror image of [findPeersEngagedBy].
     */
    @Query(
        """
        SELECT DISTINCT other.userId FROM ConversationMemberJpaEntity me, ConversationMemberJpaEntity other, ConversationJpaEntity c
        WHERE me.userId = :userId
          AND other.conversationId = me.conversationId
          AND other.userId <> :userId
          AND c.id = me.conversationId
          AND c.type IN :types
          AND (c.createdBy = other.userId OR EXISTS (
                SELECT 1 FROM MessageJpaEntity msg WHERE msg.conversationId = c.id AND msg.senderId = other.userId))
        """
    )
    fun findUsersWhoEngagedWith(userId: UUID, types: List<ConversationType>): Set<UUID>

    /**
     * Whether the two users are both members of a conversation whose type is in [types]. The
     * types are a parameter rather than literals in the query because fully-qualified enum
     * literals in JPQL `IN` clauses are unreliable under Hibernate 6.
     */
    @Query(
        """
        SELECT COUNT(m) > 0 FROM ConversationMemberJpaEntity m, ConversationJpaEntity c
        WHERE c.id = m.conversationId
          AND c.type IN :types
          AND m.userId = :otherUserId
          AND m.conversationId IN (SELECT m2.conversationId FROM ConversationMemberJpaEntity m2 WHERE m2.userId = :userId)
        """
    )
    fun sharesConversationOfType(
        userId: UUID,
        otherUserId: UUID,
        types: List<ConversationType>,
    ): Boolean

    @Modifying
    @Query("UPDATE ConversationMemberJpaEntity m SET m.lastReadAt = :timestamp WHERE m.conversationId = :conversationId AND m.userId = :userId")
    fun updateLastReadAt(conversationId: UUID, userId: UUID, timestamp: Instant)

    @Modifying
    @Query("DELETE FROM ConversationMemberJpaEntity m WHERE m.conversationId = :conversationId AND m.userId = :userId")
    fun deleteByConversationIdAndUserId(conversationId: UUID, userId: UUID)

    @Modifying
    @Query("UPDATE ConversationMemberJpaEntity m SET m.role = :role WHERE m.conversationId = :conversationId AND m.userId = :userId")
    fun updateRole(conversationId: UUID, userId: UUID, role: com.muhabbet.messaging.domain.model.MemberRole)
}

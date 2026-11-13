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

    @Query("SELECT DISTINCT m.userId FROM ConversationMemberJpaEntity m WHERE m.conversationId IN (SELECT m2.conversationId FROM ConversationMemberJpaEntity m2 WHERE m2.userId = :userId) AND m.userId != :userId")
    fun findAllContactUserIds(userId: UUID): Set<UUID>

    /**
     * Which of [candidateIds] share a DIRECT conversation with [viewerId] in which **they** made
     * themselves known to the viewer — they created it, or they have sent a message in it. See
     * `ConversationRepository.findDirectCounterpartsWhoDisclosed` for why membership alone is not
     * enough. A scheduled message that has not been delivered yet does not count: the viewer has
     * not received it, so nothing has been disclosed.
     */
    @Query(
        """
        SELECT DISTINCT other.userId
        FROM ConversationMemberJpaEntity viewer, ConversationMemberJpaEntity other, ConversationJpaEntity c
        WHERE viewer.userId = :viewerId
          AND other.conversationId = viewer.conversationId
          AND other.userId IN :candidateIds
          AND other.userId <> :viewerId
          AND c.id = viewer.conversationId
          AND c.type = :directType
          AND (
            c.createdBy = other.userId
            OR EXISTS (
              SELECT 1 FROM MessageJpaEntity msg
              WHERE msg.conversationId = c.id
                AND msg.senderId = other.userId
                AND msg.isScheduled = false
            )
          )
        """
    )
    fun findDirectCounterpartsWhoDisclosed(
        viewerId: UUID,
        candidateIds: Collection<UUID>,
        directType: ConversationType
    ): Set<UUID>

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

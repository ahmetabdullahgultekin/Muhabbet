package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.ConversationMemberId
import com.muhabbet.messaging.adapter.out.persistence.entity.ConversationMemberJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataConversationMemberRepository : JpaRepository<ConversationMemberJpaEntity, ConversationMemberId> {
    fun findByConversationId(conversationId: UUID): List<ConversationMemberJpaEntity>
    fun countByConversationId(conversationId: UUID): Long
    fun findByConversationIdIn(conversationIds: List<UUID>): List<ConversationMemberJpaEntity>
    fun findByUserId(userId: UUID): List<ConversationMemberJpaEntity>
    fun findByConversationIdAndUserId(conversationId: UUID, userId: UUID): ConversationMemberJpaEntity?

    @Query("SELECT DISTINCT m.userId FROM ConversationMemberJpaEntity m WHERE m.conversationId IN (SELECT m2.conversationId FROM ConversationMemberJpaEntity m2 WHERE m2.userId = :userId) AND m.userId != :userId")
    fun findAllContactUserIds(userId: UUID): Set<UUID>

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

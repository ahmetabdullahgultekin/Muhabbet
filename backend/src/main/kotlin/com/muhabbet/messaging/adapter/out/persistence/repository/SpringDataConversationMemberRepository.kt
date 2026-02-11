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
    fun findByUserId(userId: UUID): List<ConversationMemberJpaEntity>
    fun findByConversationIdAndUserId(conversationId: UUID, userId: UUID): ConversationMemberJpaEntity?

    @Modifying
    @Query("UPDATE ConversationMemberJpaEntity m SET m.lastReadAt = :timestamp WHERE m.conversationId = :conversationId AND m.userId = :userId")
    fun updateLastReadAt(conversationId: UUID, userId: UUID, timestamp: Instant)
}

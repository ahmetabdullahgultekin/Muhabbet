package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupJoinRequestJpaEntity
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataGroupJoinRequestRepository : JpaRepository<GroupJoinRequestJpaEntity, UUID> {
    fun findByConversationIdAndStatus(conversationId: UUID, status: JoinRequestStatus): List<GroupJoinRequestJpaEntity>
    fun findByConversationIdAndUserIdAndStatus(conversationId: UUID, userId: UUID, status: JoinRequestStatus): GroupJoinRequestJpaEntity?
}

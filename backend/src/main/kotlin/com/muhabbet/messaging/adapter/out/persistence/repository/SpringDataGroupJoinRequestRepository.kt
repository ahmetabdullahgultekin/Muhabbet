package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupJoinRequestJpaEntity
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataGroupJoinRequestRepository : JpaRepository<GroupJoinRequestJpaEntity, UUID> {
    fun findByConversationIdAndStatus(conversationId: UUID, status: JoinRequestStatus): List<GroupJoinRequestJpaEntity>
    fun findByConversationIdAndUserIdAndStatus(conversationId: UUID, userId: UUID, status: JoinRequestStatus): GroupJoinRequestJpaEntity?

    /** Conditional on PENDING; 0 rows means another review got there first. */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE group_join_requests
            SET status = :status, reviewed_by = :reviewedBy, reviewed_at = :reviewedAt
            WHERE id = :id AND status = 'PENDING'
        """,
        nativeQuery = true
    )
    fun markReviewed(id: UUID, status: String, reviewedBy: UUID, reviewedAt: Instant): Int
}

package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "group_join_requests")
class GroupJoinRequestJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "conversation_id", nullable = false)
    val conversationId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "invite_link_id")
    val inviteLinkId: UUID? = null,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: JoinRequestStatus = JoinRequestStatus.PENDING,

    @Column(name = "reviewed_by")
    var reviewedBy: UUID? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null
) {
    fun toDomain(): GroupJoinRequest = GroupJoinRequest(
        id = id, conversationId = conversationId, userId = userId,
        inviteLinkId = inviteLinkId, status = status, reviewedBy = reviewedBy,
        createdAt = createdAt, reviewedAt = reviewedAt
    )

    companion object {
        fun fromDomain(r: GroupJoinRequest): GroupJoinRequestJpaEntity = GroupJoinRequestJpaEntity(
            id = r.id, conversationId = r.conversationId, userId = r.userId,
            inviteLinkId = r.inviteLinkId, status = r.status, reviewedBy = r.reviewedBy,
            createdAt = r.createdAt, reviewedAt = r.reviewedAt
        )
    }
}

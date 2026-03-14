package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

enum class JoinRequestStatus {
    PENDING, APPROVED, REJECTED
}

data class GroupJoinRequest(
    val id: UUID = UUID.randomUUID(),
    val conversationId: UUID,
    val userId: UUID,
    val inviteLinkId: UUID? = null,
    val status: JoinRequestStatus = JoinRequestStatus.PENDING,
    val reviewedBy: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val reviewedAt: Instant? = null
)

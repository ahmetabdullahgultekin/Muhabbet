package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import java.util.UUID

interface GroupJoinRequestRepository {
    fun save(request: GroupJoinRequest): GroupJoinRequest
    fun findById(id: UUID): GroupJoinRequest?
    fun findPendingByConversationId(conversationId: UUID): List<GroupJoinRequest>
    fun findByConversationIdAndUserIdAndStatus(conversationId: UUID, userId: UUID, status: JoinRequestStatus): GroupJoinRequest?

    /**
     * Writes [reviewed]'s status, reviewer and time **only if the stored row is still PENDING**,
     * returning whether it did. The domain transition already refuses a non-PENDING request it has
     * read; this is the half that holds when two admins read the same PENDING row at once — exactly
     * one of them gets `true` (#888).
     */
    fun markReviewed(reviewed: GroupJoinRequest): Boolean
}

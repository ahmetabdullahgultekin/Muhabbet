package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import java.util.UUID

interface GroupJoinRequestRepository {
    fun save(request: GroupJoinRequest): GroupJoinRequest
    fun findById(id: UUID): GroupJoinRequest?
    fun findPendingByConversationId(conversationId: UUID): List<GroupJoinRequest>
    fun findByConversationIdAndUserIdAndStatus(conversationId: UUID, userId: UUID, status: JoinRequestStatus): GroupJoinRequest?
}

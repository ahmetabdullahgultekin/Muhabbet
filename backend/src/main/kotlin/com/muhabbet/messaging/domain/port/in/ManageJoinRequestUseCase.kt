package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.GroupJoinRequest
import java.util.UUID

interface ManageJoinRequestUseCase {
    fun requestJoin(conversationId: UUID, userId: UUID, linkId: UUID?): GroupJoinRequest
    fun approveJoin(requestId: UUID, adminId: UUID)
    fun rejectJoin(requestId: UUID, adminId: UUID)
    fun getPendingRequests(conversationId: UUID): List<GroupJoinRequest>
}

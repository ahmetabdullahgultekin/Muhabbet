package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.GroupJoinRequest
import java.util.UUID

interface ManageJoinRequestUseCase {
    fun requestJoin(conversationId: UUID, userId: UUID, linkId: UUID?): GroupJoinRequest
    fun approveJoin(requestId: UUID, adminId: UUID)
    fun rejectJoin(requestId: UUID, adminId: UUID)
    /** Pending requests for [conversationId]; only its admins and owner may read them (#888). */
    fun getPendingRequests(conversationId: UUID, requesterId: UUID): List<GroupJoinRequest>
}

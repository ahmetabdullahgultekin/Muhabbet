package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.GroupJoinRequest
import java.util.UUID

interface ManageJoinRequestUseCase {
    fun requestJoin(conversationId: UUID, userId: UUID, linkId: UUID?): GroupJoinRequest

    /**
     * Admits the requester. [conversationId] is the group the caller is acting in: a request that
     * belongs to another group, or that is no longer pending, is reported as not found (#788).
     */
    fun approveJoin(conversationId: UUID, requestId: UUID, adminId: UUID)

    fun rejectJoin(conversationId: UUID, requestId: UUID, adminId: UUID)

    /** Pending requests with who is asking — for the group's admins and owner only (#788). */
    fun getPendingRequests(conversationId: UUID, requesterId: UUID): List<PendingJoinRequest>
}

/**
 * A pending request plus the requester's display name and avatar, which is what an admin needs to
 * decide. Never the requester's phone number: they are not yet a member, and asking to join does
 * not hand one's number to every admin of the group.
 */
data class PendingJoinRequest(
    val request: GroupJoinRequest,
    val displayName: String?,
    val avatarUrl: String?
)

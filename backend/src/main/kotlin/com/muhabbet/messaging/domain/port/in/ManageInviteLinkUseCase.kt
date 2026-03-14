package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.GroupInviteLink
import java.time.Instant
import java.util.UUID

interface ManageInviteLinkUseCase {
    fun createLink(conversationId: UUID, userId: UUID, requiresApproval: Boolean, maxUses: Int?, expiresAt: Instant?): GroupInviteLink
    fun revokeLink(linkId: UUID, userId: UUID)
    fun joinViaLink(token: String, userId: UUID)
    fun getLinkInfo(token: String): GroupInviteLink
}

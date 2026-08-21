package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.GroupInviteLink
import java.time.Instant
import java.util.UUID

interface ManageInviteLinkUseCase {
    fun createLink(conversationId: UUID, userId: UUID, requiresApproval: Boolean, maxUses: Int?, expiresAt: Instant?): GroupInviteLink

    /**
     * The group's current invite link, for a caller who is inside the group.
     *
     * Distinct from [getLinkInfo], which answers "what is behind this token" for someone who was
     * handed a URL. This one answers "what link does my group have", which is the question the
     * invite sheet asks when it opens and which nothing could answer before (#705).
     *
     * Throws `INVITE_LINK_NOT_FOUND` when the group has no active link, rather than returning
     * null: a group nobody has shared yet is the normal state, the client already reads 404 as
     * "offer to create one", and a nullable return would let a caller forget the case.
     */
    fun getActiveLink(conversationId: UUID, userId: UUID): GroupInviteLink

    fun revokeLink(linkId: UUID, userId: UUID)
    fun joinViaLink(token: String, userId: UUID)
    fun getLinkInfo(token: String): GroupInviteLink
}

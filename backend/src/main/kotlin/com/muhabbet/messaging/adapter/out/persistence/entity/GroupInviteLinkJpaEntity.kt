package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.GroupInviteLink
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "group_invite_links")
class GroupInviteLinkJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "conversation_id", nullable = false)
    val conversationId: UUID,

    @Column(name = "invite_token", nullable = false, unique = true)
    val inviteToken: String,

    @Column(name = "created_by", nullable = false)
    val createdBy: UUID,

    @Column(name = "requires_approval", nullable = false)
    val requiresApproval: Boolean = false,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "max_uses")
    val maxUses: Int? = null,

    @Column(name = "use_count", nullable = false)
    var useCount: Int = 0,

    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): GroupInviteLink = GroupInviteLink(
        id = id, conversationId = conversationId, inviteToken = inviteToken,
        createdBy = createdBy, requiresApproval = requiresApproval, isActive = isActive,
        maxUses = maxUses, useCount = useCount, expiresAt = expiresAt, createdAt = createdAt
    )

    companion object {
        fun fromDomain(link: GroupInviteLink): GroupInviteLinkJpaEntity = GroupInviteLinkJpaEntity(
            id = link.id, conversationId = link.conversationId, inviteToken = link.inviteToken,
            createdBy = link.createdBy, requiresApproval = link.requiresApproval, isActive = link.isActive,
            maxUses = link.maxUses, useCount = link.useCount, expiresAt = link.expiresAt, createdAt = link.createdAt
        )
    }
}

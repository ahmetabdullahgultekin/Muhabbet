package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.CommunityInviteLink
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "community_invite_links")
class CommunityInviteLinkJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "community_id", nullable = false)
    val communityId: UUID,

    @Column(name = "invite_token", nullable = false, unique = true)
    val inviteToken: String,

    @Column(name = "created_by", nullable = false)
    val createdBy: UUID,

    // `var` on exactly the two columns that change after insert: a link is revoked, and its use
    // count rises. Everything else about a link is decided when it is minted.
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
    fun toDomain(): CommunityInviteLink = CommunityInviteLink(
        id = id, communityId = communityId, inviteToken = inviteToken, createdBy = createdBy,
        isActive = isActive, maxUses = maxUses, useCount = useCount,
        expiresAt = expiresAt, createdAt = createdAt
    )

    companion object {
        fun fromDomain(link: CommunityInviteLink): CommunityInviteLinkJpaEntity = CommunityInviteLinkJpaEntity(
            id = link.id, communityId = link.communityId, inviteToken = link.inviteToken,
            createdBy = link.createdBy, isActive = link.isActive, maxUses = link.maxUses,
            useCount = link.useCount, expiresAt = link.expiresAt, createdAt = link.createdAt
        )
    }
}

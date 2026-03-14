package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.CommunityGroup
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class CommunityGroupId(
    val communityId: UUID = UUID.randomUUID(),
    val conversationId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "community_groups")
@IdClass(CommunityGroupId::class)
class CommunityGroupJpaEntity(
    @Id
    @Column(name = "community_id")
    val communityId: UUID,

    @Id
    @Column(name = "conversation_id")
    val conversationId: UUID,

    @Column(name = "added_at", nullable = false)
    val addedAt: Instant = Instant.now()
) {
    fun toDomain(): CommunityGroup = CommunityGroup(
        communityId = communityId, conversationId = conversationId, addedAt = addedAt
    )

    companion object {
        fun fromDomain(cg: CommunityGroup): CommunityGroupJpaEntity = CommunityGroupJpaEntity(
            communityId = cg.communityId, conversationId = cg.conversationId, addedAt = cg.addedAt
        )
    }
}

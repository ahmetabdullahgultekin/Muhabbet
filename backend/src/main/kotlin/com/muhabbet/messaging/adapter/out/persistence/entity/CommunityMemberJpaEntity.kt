package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.MemberRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class CommunityMemberId(
    val communityId: UUID = UUID.randomUUID(),
    val userId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "community_members")
@IdClass(CommunityMemberId::class)
class CommunityMemberJpaEntity(
    @Id
    @Column(name = "community_id")
    val communityId: UUID,

    @Id
    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    var role: MemberRole = MemberRole.MEMBER,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now()
) {
    fun toDomain(): CommunityMember = CommunityMember(
        communityId = communityId, userId = userId, role = role, joinedAt = joinedAt
    )

    companion object {
        fun fromDomain(cm: CommunityMember): CommunityMemberJpaEntity = CommunityMemberJpaEntity(
            communityId = cm.communityId, userId = cm.userId, role = cm.role, joinedAt = cm.joinedAt
        )
    }
}

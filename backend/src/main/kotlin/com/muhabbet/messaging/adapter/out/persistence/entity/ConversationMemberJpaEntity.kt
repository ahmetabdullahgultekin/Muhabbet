package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.ConversationMember
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

data class ConversationMemberId(
    val conversationId: UUID = UUID.randomUUID(),
    val userId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "conversation_members")
@IdClass(ConversationMemberId::class)
class ConversationMemberJpaEntity(
    @Id
    @Column(name = "conversation_id")
    val conversationId: UUID,

    @Id
    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    val role: MemberRole = MemberRole.MEMBER,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),

    @Column(name = "muted_until")
    var mutedUntil: Instant? = null,

    @Column(name = "last_read_at")
    var lastReadAt: Instant? = null
) {
    fun toDomain(): ConversationMember = ConversationMember(
        conversationId = conversationId, userId = userId, role = role,
        joinedAt = joinedAt, mutedUntil = mutedUntil, lastReadAt = lastReadAt
    )

    companion object {
        fun fromDomain(m: ConversationMember): ConversationMemberJpaEntity = ConversationMemberJpaEntity(
            conversationId = m.conversationId, userId = m.userId, role = m.role,
            joinedAt = m.joinedAt, mutedUntil = m.mutedUntil, lastReadAt = m.lastReadAt
        )
    }
}

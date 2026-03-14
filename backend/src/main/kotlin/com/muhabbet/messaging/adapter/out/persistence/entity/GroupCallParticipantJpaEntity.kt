package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.GroupCallParticipant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class GroupCallParticipantId(
    val callId: String = "",
    val userId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "group_call_participants")
@IdClass(GroupCallParticipantId::class)
class GroupCallParticipantJpaEntity(
    @Id
    @Column(name = "call_id")
    val callId: String,

    @Id
    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),

    @Column(name = "left_at")
    var leftAt: Instant? = null
) {
    fun toDomain(): GroupCallParticipant = GroupCallParticipant(
        callId = callId, userId = userId, joinedAt = joinedAt, leftAt = leftAt
    )

    companion object {
        fun fromDomain(p: GroupCallParticipant): GroupCallParticipantJpaEntity = GroupCallParticipantJpaEntity(
            callId = p.callId, userId = p.userId, joinedAt = p.joinedAt, leftAt = p.leftAt
        )
    }
}

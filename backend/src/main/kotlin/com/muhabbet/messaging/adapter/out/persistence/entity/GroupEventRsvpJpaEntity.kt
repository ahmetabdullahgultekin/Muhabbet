package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.GroupEventRsvp
import com.muhabbet.messaging.domain.model.RsvpStatus
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

data class GroupEventRsvpId(
    val eventId: UUID = UUID.randomUUID(),
    val userId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "group_event_rsvps")
@IdClass(GroupEventRsvpId::class)
class GroupEventRsvpJpaEntity(
    @Id
    @Column(name = "event_id")
    val eventId: UUID,

    @Id
    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: RsvpStatus = RsvpStatus.GOING,

    @Column(name = "responded_at", nullable = false)
    var respondedAt: Instant = Instant.now()
) {
    fun toDomain(): GroupEventRsvp = GroupEventRsvp(
        eventId = eventId, userId = userId, status = status, respondedAt = respondedAt
    )

    companion object {
        fun fromDomain(r: GroupEventRsvp): GroupEventRsvpJpaEntity = GroupEventRsvpJpaEntity(
            eventId = r.eventId, userId = r.userId, status = r.status, respondedAt = r.respondedAt
        )
    }
}

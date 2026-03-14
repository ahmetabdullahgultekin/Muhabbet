package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.GroupEvent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "group_events")
class GroupEventJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "conversation_id", nullable = false)
    val conversationId: UUID,

    @Column(name = "created_by", nullable = false)
    val createdBy: UUID,

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "event_time", nullable = false)
    var eventTime: Instant,

    @Column(name = "location")
    var location: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): GroupEvent = GroupEvent(
        id = id, conversationId = conversationId, createdBy = createdBy,
        title = title, description = description, eventTime = eventTime,
        location = location, createdAt = createdAt
    )

    companion object {
        fun fromDomain(e: GroupEvent): GroupEventJpaEntity = GroupEventJpaEntity(
            id = e.id, conversationId = e.conversationId, createdBy = e.createdBy,
            title = e.title, description = e.description, eventTime = e.eventTime,
            location = e.location, createdAt = e.createdAt
        )
    }
}

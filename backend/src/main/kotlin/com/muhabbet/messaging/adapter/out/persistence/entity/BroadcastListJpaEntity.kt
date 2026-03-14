package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.BroadcastList
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "broadcast_lists")
class BroadcastListJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "owner_id", nullable = false)
    val ownerId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): BroadcastList = BroadcastList(
        id = id, ownerId = ownerId, name = name, createdAt = createdAt
    )

    companion object {
        fun fromDomain(bl: BroadcastList): BroadcastListJpaEntity = BroadcastListJpaEntity(
            id = bl.id, ownerId = bl.ownerId, name = bl.name, createdAt = bl.createdAt
        )
    }
}

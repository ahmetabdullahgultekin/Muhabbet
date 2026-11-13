package com.muhabbet.messaging.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A row of `status_views` (V28, #787). Written only by the native upsert in its repository. */
@Entity
@Table(name = "status_views")
class StatusViewJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "status_id", nullable = false)
    val statusId: UUID,

    @Column(name = "viewer_id", nullable = false)
    val viewerId: UUID,

    @Column(name = "viewed_at", nullable = false)
    val viewedAt: Instant = Instant.now()
)

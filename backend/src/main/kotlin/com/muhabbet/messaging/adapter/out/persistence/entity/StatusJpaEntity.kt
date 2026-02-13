package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.Status
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "statuses")
class StatusJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "content")
    val content: String? = null,

    @Column(name = "media_url")
    val mediaUrl: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.now().plusSeconds(86400)
) {
    fun toDomain(): Status = Status(
        id = id, userId = userId, content = content,
        mediaUrl = mediaUrl, createdAt = createdAt, expiresAt = expiresAt
    )

    companion object {
        fun fromDomain(s: Status): StatusJpaEntity = StatusJpaEntity(
            id = s.id, userId = s.userId, content = s.content,
            mediaUrl = s.mediaUrl, createdAt = s.createdAt, expiresAt = s.expiresAt
        )
    }
}

package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.Community
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "communities")
class CommunityJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "created_by", nullable = false)
    val createdBy: UUID,

    @Column(name = "announcement_group_id")
    var announcementGroupId: UUID? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun toDomain(): Community = Community(
        id = id, name = name, description = description, avatarUrl = avatarUrl,
        createdBy = createdBy, announcementGroupId = announcementGroupId,
        createdAt = createdAt, updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(c: Community): CommunityJpaEntity = CommunityJpaEntity(
            id = c.id, name = c.name, description = c.description, avatarUrl = c.avatarUrl,
            createdBy = c.createdBy, announcementGroupId = c.announcementGroupId,
            createdAt = c.createdAt, updatedAt = c.updatedAt
        )
    }
}

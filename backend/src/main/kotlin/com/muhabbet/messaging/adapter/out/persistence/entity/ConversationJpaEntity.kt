package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "conversations")
class ConversationJpaEntity(
    @Id
    val id: UUID,

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    val type: ConversationType,

    @Column(name = "name")
    var name: String? = null,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "created_by")
    val createdBy: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun toDomain(): Conversation = Conversation(
        id = id, type = type, name = name, avatarUrl = avatarUrl,
        description = description, createdBy = createdBy, createdAt = createdAt, updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(c: Conversation): ConversationJpaEntity = ConversationJpaEntity(
            id = c.id, type = c.type, name = c.name, avatarUrl = c.avatarUrl,
            description = c.description, createdBy = c.createdBy, createdAt = c.createdAt, updatedAt = c.updatedAt
        )
    }
}

package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.Reaction
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "message_reactions")
class MessageReactionJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "message_id", nullable = false)
    val messageId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "emoji", nullable = false)
    val emoji: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): Reaction = Reaction(
        id = id, messageId = messageId, userId = userId,
        emoji = emoji, createdAt = createdAt
    )

    companion object {
        fun fromDomain(r: Reaction): MessageReactionJpaEntity = MessageReactionJpaEntity(
            id = r.id, messageId = r.messageId, userId = r.userId,
            emoji = r.emoji, createdAt = r.createdAt
        )
    }
}

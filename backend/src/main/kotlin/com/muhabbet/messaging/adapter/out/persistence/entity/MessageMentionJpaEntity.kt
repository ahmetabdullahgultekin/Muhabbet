package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.Mention
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for a single @mention row (`message_mentions`, migration V19).
 * Tier-2 group @mentions — see `docs/design/T2-group-mentions.md`, ADR-0008.
 * JPA entity ≠ domain model: maps to/from [Mention].
 */
@Entity
@Table(name = "message_mentions")
class MessageMentionJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "message_id", nullable = false)
    val messageId: UUID,

    @Column(name = "mentioned_user_id", nullable = false)
    val mentionedUserId: UUID,

    @Column(name = "start_offset", nullable = false)
    val startOffset: Int,

    @Column(name = "length", nullable = false)
    val length: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): Mention = Mention(
        mentionedUserId = mentionedUserId,
        startOffset = startOffset,
        length = length
    )

    companion object {
        fun fromDomain(messageId: UUID, m: Mention): MessageMentionJpaEntity = MessageMentionJpaEntity(
            messageId = messageId,
            mentionedUserId = m.mentionedUserId,
            startOffset = m.startOffset,
            length = m.length
        )
    }
}

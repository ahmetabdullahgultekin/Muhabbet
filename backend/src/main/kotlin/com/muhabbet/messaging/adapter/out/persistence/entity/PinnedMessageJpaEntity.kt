package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.PinnedMessage
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class PinnedMessageId(
    val conversationId: UUID = UUID.randomUUID(),
    val messageId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "pinned_messages")
@IdClass(PinnedMessageId::class)
class PinnedMessageJpaEntity(
    @Id
    @Column(name = "conversation_id")
    val conversationId: UUID,

    @Id
    @Column(name = "message_id")
    val messageId: UUID,

    @Column(name = "pinned_by", nullable = false)
    val pinnedBy: UUID,

    @Column(name = "pinned_at", nullable = false)
    val pinnedAt: Instant = Instant.now()
) {
    fun toDomain(): PinnedMessage = PinnedMessage(
        conversationId = conversationId, messageId = messageId, pinnedBy = pinnedBy, pinnedAt = pinnedAt
    )

    companion object {
        fun fromDomain(p: PinnedMessage): PinnedMessageJpaEntity = PinnedMessageJpaEntity(
            conversationId = p.conversationId, messageId = p.messageId,
            pinnedBy = p.pinnedBy, pinnedAt = p.pinnedAt
        )
    }
}

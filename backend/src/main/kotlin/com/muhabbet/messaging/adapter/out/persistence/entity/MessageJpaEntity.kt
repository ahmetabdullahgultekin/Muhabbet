package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "messages")
class MessageJpaEntity(
    @Id
    val id: UUID,

    @Column(name = "conversation_id", nullable = false)
    val conversationId: UUID,

    @Column(name = "sender_id", nullable = false)
    val senderId: UUID,

    @Column(name = "content_type", nullable = false)
    @Enumerated(EnumType.STRING)
    val contentType: ContentType = ContentType.TEXT,

    @Column(name = "content", nullable = false)
    val content: String,

    @Column(name = "reply_to_id")
    val replyToId: UUID? = null,

    @Column(name = "media_url")
    val mediaUrl: String? = null,

    @Column(name = "thumbnail_url")
    val thumbnailUrl: String? = null,

    @Column(name = "server_timestamp", nullable = false)
    val serverTimestamp: Instant = Instant.now(),

    @Column(name = "client_timestamp", nullable = false)
    val clientTimestamp: Instant,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
) {
    fun toDomain(): Message = Message(
        id = id, conversationId = conversationId, senderId = senderId,
        contentType = contentType, content = content, replyToId = replyToId,
        mediaUrl = mediaUrl, thumbnailUrl = thumbnailUrl,
        serverTimestamp = serverTimestamp, clientTimestamp = clientTimestamp,
        isDeleted = isDeleted, deletedAt = deletedAt
    )

    companion object {
        fun fromDomain(m: Message): MessageJpaEntity = MessageJpaEntity(
            id = m.id, conversationId = m.conversationId, senderId = m.senderId,
            contentType = m.contentType, content = m.content, replyToId = m.replyToId,
            mediaUrl = m.mediaUrl, thumbnailUrl = m.thumbnailUrl,
            serverTimestamp = m.serverTimestamp, clientTimestamp = m.clientTimestamp,
            isDeleted = m.isDeleted, deletedAt = m.deletedAt
        )
    }
}

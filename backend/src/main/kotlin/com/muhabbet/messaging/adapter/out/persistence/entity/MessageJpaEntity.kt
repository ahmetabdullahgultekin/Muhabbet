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

    @Column(name = "media_id")
    var mediaId: UUID? = null,

    @Column(name = "server_timestamp", nullable = false)
    val serverTimestamp: Instant = Instant.now(),

    @Column(name = "client_timestamp", nullable = false)
    val clientTimestamp: Instant,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Column(name = "edited_at")
    var editedAt: Instant? = null,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "forwarded_from")
    val forwardedFrom: UUID? = null,

    // View-Once Media
    @Column(name = "view_once", nullable = false)
    var viewOnce: Boolean = false,

    @Column(name = "viewed_at")
    var viewedAt: Instant? = null,

    @Column(name = "viewed_by")
    var viewedBy: UUID? = null,

    // Message Scheduling
    @Column(name = "scheduled_at")
    var scheduledAt: Instant? = null,

    @Column(name = "is_scheduled", nullable = false)
    var isScheduled: Boolean = false
) {
    // Sequence (V28) and device-scoped idempotency (V29). Body properties rather than constructor
    // parameters so the constructor's shape is unchanged. Written once at insert (see [fromDomain]);
    // after that only the conditional @Modifying statements in SpringDataMessageRepository move them.
    @Column(name = "seq")
    var seq: Long? = null

    @Column(name = "change_seq")
    var changeSeq: Long? = null

    @Column(name = "sender_device_id")
    var senderDeviceId: UUID? = null

    // The client's id is the row id; recorded separately only where a device is known, which is
    // what the partial unique index (sender_device_id, client_message_id) keys on.
    @Column(name = "client_message_id")
    var clientMessageId: UUID? = null

    fun toDomain(): Message = Message(
        id = id, conversationId = conversationId, senderId = senderId,
        contentType = contentType, content = content, replyToId = replyToId,
        mediaUrl = mediaUrl, thumbnailUrl = thumbnailUrl, mediaId = mediaId,
        serverTimestamp = serverTimestamp, clientTimestamp = clientTimestamp,
        isDeleted = isDeleted, deletedAt = deletedAt, editedAt = editedAt,
        expiresAt = expiresAt, forwardedFrom = forwardedFrom,
        viewOnce = viewOnce, viewedAt = viewedAt, viewedBy = viewedBy,
        scheduledAt = scheduledAt, isScheduled = isScheduled,
        seq = seq, changeSeq = changeSeq, senderDeviceId = senderDeviceId
    )

    companion object {
        fun fromDomain(m: Message): MessageJpaEntity = MessageJpaEntity(
            id = m.id, conversationId = m.conversationId, senderId = m.senderId,
            contentType = m.contentType, content = m.content, replyToId = m.replyToId,
            mediaUrl = m.mediaUrl, thumbnailUrl = m.thumbnailUrl, mediaId = m.mediaId,
            serverTimestamp = m.serverTimestamp, clientTimestamp = m.clientTimestamp,
            isDeleted = m.isDeleted, deletedAt = m.deletedAt, editedAt = m.editedAt,
            expiresAt = m.expiresAt, forwardedFrom = m.forwardedFrom,
            viewOnce = m.viewOnce, viewedAt = m.viewedAt, viewedBy = m.viewedBy,
            scheduledAt = m.scheduledAt, isScheduled = m.isScheduled
        ).apply {
            seq = m.seq
            changeSeq = m.changeSeq
            senderDeviceId = m.senderDeviceId
            clientMessageId = m.senderDeviceId?.let { m.id }
        }
    }
}

package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

enum class ContentType {
    TEXT, IMAGE, VOICE, VIDEO, DOCUMENT, LOCATION, CONTACT, POLL, STICKER, GIF
}

enum class DeliveryStatus {
    SENT, DELIVERED, READ
}

data class Message(
    val id: UUID,
    val conversationId: UUID,
    val senderId: UUID,
    val contentType: ContentType = ContentType.TEXT,
    val content: String,
    val replyToId: UUID? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    /**
     * The `media_files` row this message's blob lives in (#541).
     *
     * [mediaUrl] is a rendering convenience — a presigned URL that expires on its own schedule and,
     * for a view-once photo, outlived the burn by seven days. This is the reference that lets the
     * bytes actually be destroyed.
     *
     * **Client-asserted, and worth nothing on its own.** `ViewOnceService` confirms the object was
     * this message's sender's upload immediately before it deletes anything; nowhere else acts on
     * it. Any future reader owes the same check. Null for text, and for every message sent before
     * V24.
     */
    val mediaId: UUID? = null,
    val serverTimestamp: Instant = Instant.now(),
    val clientTimestamp: Instant,
    val isDeleted: Boolean = false,
    val deletedAt: Instant? = null,
    val editedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val forwardedFrom: UUID? = null,
    // View-Once Media
    val viewOnce: Boolean = false,
    val viewedAt: Instant? = null,
    val viewedBy: UUID? = null,
    // Message Scheduling
    val scheduledAt: Instant? = null,
    val isScheduled: Boolean = false,
    /**
     * Position in the conversation (#750, ADR 0014), allocated from `conversations.last_seq` inside
     * the send transaction. Null for a scheduled message until it is released, and for rows written
     * before V28 that were never numbered.
     */
    val seq: Long? = null,
    /** The last change to this row — the insert, an edit or a delete — from the same counter. */
    val changeSeq: Long? = null,
    /**
     * The device that sent it (V29). Together with [id] — the client's own message id — it is the
     * idempotency key a retried send is matched on. Null for legacy rows.
     */
    val senderDeviceId: UUID? = null
)

data class MessageDeliveryStatus(
    val messageId: UUID,
    val userId: UUID,
    val status: DeliveryStatus = DeliveryStatus.SENT,
    val updatedAt: Instant = Instant.now()
)

package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

enum class ContentType {
    TEXT, IMAGE, VOICE, VIDEO, DOCUMENT, LOCATION, CONTACT
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
    val serverTimestamp: Instant = Instant.now(),
    val clientTimestamp: Instant,
    val isDeleted: Boolean = false,
    val deletedAt: Instant? = null,
    val editedAt: Instant? = null,
    val expiresAt: Instant? = null
)

data class MessageDeliveryStatus(
    val messageId: UUID,
    val userId: UUID,
    val status: DeliveryStatus = DeliveryStatus.SENT,
    val updatedAt: Instant = Instant.now()
)

package com.muhabbet.messaging.domain.event

import com.muhabbet.messaging.domain.model.DeliveryStatus
import java.time.Instant
import java.util.UUID

data class MessageSentEvent(
    val messageId: UUID,
    val conversationId: UUID,
    val senderId: UUID,
    val content: String,
    val contentType: String,
    val serverTimestamp: Instant,
    val recipientIds: List<UUID>
)

data class MessageDeliveredEvent(
    val messageId: UUID,
    val conversationId: UUID,
    val userId: UUID,
    val status: DeliveryStatus,
    val timestamp: Instant = Instant.now()
)

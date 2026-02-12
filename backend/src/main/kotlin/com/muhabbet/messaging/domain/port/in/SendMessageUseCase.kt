package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import java.time.Instant
import java.util.UUID

interface SendMessageUseCase {
    fun sendMessage(command: SendMessageCommand): Message
}

data class SendMessageCommand(
    val messageId: UUID,
    val conversationId: UUID,
    val senderId: UUID,
    val content: String,
    val contentType: ContentType = ContentType.TEXT,
    val replyToId: UUID? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val clientTimestamp: Instant
)

package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Mention
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
    val clientTimestamp: Instant,
    val forwardedFrom: UUID? = null,
    val viewOnce: Boolean = false,
    val scheduledAt: Instant? = null,
    // @mentions (Tier 2 — gated on muhabbet.mentions.enabled; ignored when the flag is OFF)
    val mentions: List<Mention> = emptyList(),
    val mentionsEveryone: Boolean = false
)

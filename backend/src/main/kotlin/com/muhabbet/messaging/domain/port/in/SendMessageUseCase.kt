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
    /**
     * The media object this message points at, as the client reported it (#541). Stored verbatim
     * and verified where it is used: the burn path confirms the object was this sender's own upload
     * before destroying anything, because that is what this reference exists to make possible.
     */
    val mediaId: UUID? = null,
    val clientTimestamp: Instant,
    val forwardedFrom: UUID? = null,
    val viewOnce: Boolean = false,
    val scheduledAt: Instant? = null
)

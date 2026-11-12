package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import java.time.Instant
import java.util.UUID

interface SendMessageUseCase {
    fun sendMessage(command: SendMessageCommand): SendResult
}

/**
 * What a send produced.
 *
 * [replay] is true when this exact send — the same device, the same client message id — had
 * already been committed, and [message] is that original row (#750, ADR 0013 §3). The caller acks
 * it exactly as it acked the first time, with the original `seq` and server timestamp, so a client
 * that lost its ack and retried learns the outcome instead of being told MSG_DUPLICATE. Nothing is
 * written and nothing is fanned out again for a replay.
 */
data class SendResult(val message: Message, val replay: Boolean = false)

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
    val scheduledAt: Instant? = null,
    /**
     * The device the send came from, from the access token — never from the frame. Half of the
     * idempotency key (V29); null only for callers that have no device, which then fall back to
     * matching a retry by sender alone.
     */
    val senderDeviceId: UUID? = null
)

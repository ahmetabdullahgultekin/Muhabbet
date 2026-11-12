package com.muhabbet.app.data.remote

import com.muhabbet.shared.model.Message
import com.muhabbet.shared.protocol.WsMessage
import kotlin.time.Instant

/**
 * A `message.new` frame as the [Message] the rest of the app works with.
 *
 * One place, because two readers now build the same thing from the same frame — the open chat's
 * bubble and the delivery pump's cache row — and a field one of them forgets is a bug the other
 * does not have: `viewOnce` (#515) and `expiresAt` (#513) were each once missing from exactly one
 * such copy.
 *
 * @param receivedAt stands in for the client timestamp, which a received frame does not carry.
 */
fun WsMessage.NewMessage.toMessage(receivedAt: Instant): Message = Message(
    id = messageId,
    conversationId = conversationId,
    senderId = senderId,
    contentType = contentType,
    content = content,
    replyToId = replyToId,
    mediaUrl = mediaUrl,
    thumbnailUrl = thumbnailUrl,
    serverTimestamp = Instant.fromEpochMilliseconds(serverTimestamp),
    clientTimestamp = receivedAt,
    forwardedFrom = forwardedFrom,
    viewOnce = viewOnce,
    expiresAt = expiresAt?.let { Instant.fromEpochMilliseconds(it) }
)

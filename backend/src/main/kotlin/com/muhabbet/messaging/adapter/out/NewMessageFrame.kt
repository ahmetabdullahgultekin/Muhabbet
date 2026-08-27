package com.muhabbet.messaging.adapter.out

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.shared.model.ContentType as SharedContentType
import com.muhabbet.shared.protocol.WsMessage

/**
 * The `message.new` frame every broadcaster puts on the socket, built in one place.
 *
 * There were two copies of this, twenty lines each, in [NoOpMessageBroadcaster] and
 * `RedisMessageBroadcaster`, with a comment in each saying they were kept in step by hand. They are
 * the live half of what `MessageMapper.toSharedMessage` is for the REST half, and the same defect
 * has now landed in this shape twice: #515 reached the database and stopped, and #513 was written
 * to the row and never told to anybody. A field added to one copy and forgotten on the other is
 * invisible in review and produces a message that behaves differently depending on which
 * broadcaster bean happens to be wired — `@Primary` picks Redis in production and the NoOp is what
 * most tests see, so the two disagreeing is the worst possible split.
 *
 * @param senderName resolved once by the caller and passed in, because the caller needs it for the
 *   offline push as well and re-reading the sender row per recipient is what #492 removed.
 */
internal fun Message.toNewMessageFrame(senderName: String?): WsMessage.NewMessage = WsMessage.NewMessage(
    messageId = id.toString(),
    conversationId = conversationId.toString(),
    senderId = senderId.toString(),
    senderName = senderName,
    content = content,
    // Falls back to TEXT rather than throwing: an unrecognised type means the two enums have drifted
    // apart in a deploy, and a message that arrives as plain text is better than a broadcast that
    // dies and delivers nothing to anyone in the conversation.
    contentType = runCatching { SharedContentType.valueOf(contentType.name) }.getOrDefault(SharedContentType.TEXT),
    replyToId = replyToId?.toString(),
    // Sealed on the wire, not in the UI: a view-once frame names the flag and withholds the blob
    // URL, which is released once by POST /messages/{id}/view-once. See MessageMapper.
    mediaUrl = if (viewOnce) null else mediaUrl,
    thumbnailUrl = if (viewOnce) null else thumbnailUrl,
    serverTimestamp = serverTimestamp.toEpochMilli(),
    forwardedFrom = forwardedFrom?.toString(),
    viewOnce = viewOnce,
    // The deadline travels with the message so the recipient can remove it on time instead of
    // waiting for the sweep's broadcast, a reload, or the user navigating away (#513).
    expiresAt = expiresAt?.toEpochMilli()
)

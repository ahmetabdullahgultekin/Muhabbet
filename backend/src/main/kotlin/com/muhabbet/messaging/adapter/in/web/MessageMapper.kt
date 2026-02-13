package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.shared.model.ContentType as SharedContentType
import com.muhabbet.shared.model.Message as SharedMessage
import com.muhabbet.shared.model.MessageStatus
import kotlinx.datetime.Instant as KInstant

/**
 * Maps a backend domain Message to the shared module Message DTO.
 * Centralizes the conversion logic that was previously duplicated
 * across MessageController, SearchController, and StarredMessageController.
 */
fun Message.toSharedMessage(resolvedStatus: MessageStatus = MessageStatus.SENT): SharedMessage = SharedMessage(
    id = id.toString(),
    conversationId = conversationId.toString(),
    senderId = senderId.toString(),
    contentType = SharedContentType.valueOf(contentType.name),
    content = if (isDeleted) "" else content,
    replyToId = replyToId?.toString(),
    mediaUrl = mediaUrl,
    thumbnailUrl = thumbnailUrl,
    status = resolvedStatus,
    serverTimestamp = KInstant.fromEpochMilliseconds(serverTimestamp.toEpochMilli()),
    clientTimestamp = KInstant.fromEpochMilliseconds(clientTimestamp.toEpochMilli()),
    editedAt = editedAt?.let { KInstant.fromEpochMilliseconds(it.toEpochMilli()) },
    isDeleted = isDeleted,
    forwardedFrom = forwardedFrom?.toString()
)

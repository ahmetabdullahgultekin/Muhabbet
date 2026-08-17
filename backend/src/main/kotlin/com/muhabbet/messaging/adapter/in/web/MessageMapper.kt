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
 *
 * ### Why `viewOnce` is mapped here and the media URL is not
 *
 * This one function is what every REST reader of a message goes through — history, media grid,
 * search, starred, background sync. Until #515 it dropped `viewOnce` entirely, so no response the
 * server ever produced said a message was view-once: the column was written, and read by nothing.
 * A recipient reloading a chat got an ordinary, permanent photo, and so did the sender.
 *
 * It also carried `mediaUrl` unconditionally, which is the more serious half. A seal that hides the
 * picture while shipping a working URL for it in the same payload is a convention, not a seal — and
 * `mediaUrl` is a **presigned** URL that needs no credential to fetch. So a view-once message leaves
 * here with its media stripped, for the sender as well as the recipient. The blob is released once,
 * by `POST /messages/{id}/view-once`, in the transaction that burns it.
 */
fun Message.toSharedMessage(resolvedStatus: MessageStatus = MessageStatus.SENT): SharedMessage = SharedMessage(
    id = id.toString(),
    conversationId = conversationId.toString(),
    senderId = senderId.toString(),
    contentType = SharedContentType.valueOf(contentType.name),
    content = if (isDeleted) "" else content,
    replyToId = replyToId?.toString(),
    mediaUrl = if (viewOnce) null else mediaUrl,
    thumbnailUrl = if (viewOnce) null else thumbnailUrl,
    status = resolvedStatus,
    serverTimestamp = KInstant.fromEpochMilliseconds(serverTimestamp.toEpochMilli()),
    clientTimestamp = KInstant.fromEpochMilliseconds(clientTimestamp.toEpochMilli()),
    editedAt = editedAt?.let { KInstant.fromEpochMilliseconds(it.toEpochMilli()) },
    isDeleted = isDeleted,
    forwardedFrom = forwardedFrom?.toString(),
    viewOnce = viewOnce,
    viewOnceViewed = viewedAt != null
)

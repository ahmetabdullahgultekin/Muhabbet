package com.muhabbet.app.ui.chat

import com.muhabbet.app.data.outbox.OutboxState
import com.muhabbet.app.data.outbox.OutgoingMessage
import com.muhabbet.app.data.outbox.isConfirmed
import com.muhabbet.app.data.outbox.toMessageStatus
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.model.advance
import com.muhabbet.shared.model.retry
import kotlin.time.Instant

/**
 * The chat's list with the outbox's view of this conversation laid over it (#792).
 *
 * - A message the list already holds takes the outbox's status through the shared lattice, so a
 *   stale outbox state cannot drag a READ bubble back to SENT; the one move [advance] refuses on
 *   purpose — out of FAILED — is made here when the outbox has re-queued the message after a retry.
 * - An unconfirmed outbox message the list does not hold is appended. That is the reader the old
 *   queue never had: after a restart, or on reopening a chat, queued and failed messages used to be
 *   invisible and the user typed them again.
 *
 * Scheduled messages are left to the scheduled-messages chip, which is where the user expects them.
 */
internal fun List<Message>.withOutbox(outbox: List<OutgoingMessage>, currentUserId: String): List<Message> {
    if (outbox.isEmpty()) return this
    val byId = outbox.associateBy { it.messageId }
    val merged = map { message ->
        val held = byId[message.id] ?: return@map message
        val status = message.status.reconcile(held.state)
        if (status == message.status) message else message.copy(status = status)
    }
    val present = mapTo(HashSet()) { it.id }
    val restored = outbox
        .filter { it.messageId !in present && !it.state.isConfirmed && it.frame.scheduledAt == null }
        .map { it.toOptimisticMessage(currentUserId) }
    return if (restored.isEmpty()) merged else merged + restored
}

private fun MessageStatus.reconcile(state: OutboxState): MessageStatus {
    val target = state.toMessageStatus()
    return if (target == MessageStatus.SENDING) retry() else advance(target)
}

private fun OutgoingMessage.toOptimisticMessage(currentUserId: String): Message = Message(
    id = frame.messageId,
    conversationId = frame.conversationId,
    senderId = currentUserId,
    contentType = frame.contentType,
    content = frame.content,
    replyToId = frame.replyToId,
    mediaUrl = frame.mediaUrl,
    thumbnailUrl = frame.thumbnailUrl,
    status = state.toMessageStatus(),
    clientTimestamp = Instant.fromEpochMilliseconds(createdAtMs),
    forwardedFrom = frame.forwardedFrom,
    viewOnce = frame.viewOnce
)

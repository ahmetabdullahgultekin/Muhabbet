package com.muhabbet.app.ui.chat

import com.muhabbet.app.util.Log
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage

private const val TAG = "ReadReceipts"

/**
 * The message ids an open chat has already published a READ receipt for.
 *
 * This exists so the receipt can be moved *out* of the "this message is not already rendered" guard
 * in [ChatScreen] (#478). That guard is about drawing a bubble twice; it was also deciding whether
 * the sender ever learns the message was read, so any path that put a message into the list before
 * the WebSocket frame was handled — a refresh, the SQLDelight cache, a pagination fetch — silently
 * consumed the frame and the receipt was never sent.
 *
 * Sending on every frame instead needs its own brake, which is this: a set, not the message list.
 * Bounded, because a long-lived chat would otherwise accumulate one entry per message for the life
 * of the process; eviction is oldest-first, and re-acking a message old enough to have fallen out is
 * harmless — the server treats a READ receipt as idempotent.
 *
 * Not thread-safe, and does not need to be: every caller runs on the same WebSocket collector
 * coroutine for one conversation.
 */
class AckedMessageIds(private val maxSize: Int = DEFAULT_MAX_SIZE) {

    private val ids = LinkedHashSet<String>()

    /**
     * Records [messageId] as acked and reports whether it is the first time.
     *
     * `false` means a receipt has already gone out for it — the caller should stay quiet, unless it
     * is deliberately re-asserting (returning to the foreground does exactly that).
     */
    fun markAcked(messageId: String): Boolean {
        val isNew = ids.add(messageId)
        while (ids.size > maxSize) {
            val iterator = ids.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
        return isNew
    }

    operator fun contains(messageId: String): Boolean = messageId in ids

    val size: Int get() = ids.size

    private companion object {
        /** Comfortably more than one screenful of history, small enough to be free. */
        const val DEFAULT_MAX_SIZE = 200
    }
}

/**
 * Publishes a READ receipt for [messageId] on behalf of an open chat.
 *
 * Silent by design — a receipt is bookkeeping between two clients and there is nothing useful to
 * interrupt the reader with. It is no longer *lossy*, though: the [send] this is given in production
 * is `WsClient.sendAck`, which queues a receipt that could not reach a dead socket and replays it on
 * the next connect. That is what the old "best-effort; re-sent on the next incoming message" comment
 * promised and never delivered when no next message arrived (#478).
 *
 * [force] re-sends even for a message already in [acked]. Used when returning to the foreground,
 * where repeating the receipt is the whole intent; left `false` everywhere else so a chat that sees
 * the same frame twice does not chatter.
 *
 * [send] is a parameter rather than a `WsClient` so this decision — the one the bug lived in — can
 * be exercised without a socket.
 */
internal suspend fun sendReadReceipt(
    acked: AckedMessageIds,
    conversationId: String,
    messageId: String,
    force: Boolean = false,
    send: suspend (WsMessage.AckMessage) -> Boolean
) {
    val isFirstAck = acked.markAcked(messageId)
    if (!isFirstAck && !force) return
    val sentNow = send(
        WsMessage.AckMessage(
            messageId = messageId,
            conversationId = conversationId,
            status = MessageStatus.READ
        )
    )
    if (!sentNow) Log.d(TAG, "READ receipt for $messageId queued for the next reconnect")
}

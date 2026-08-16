package com.muhabbet.app.data.remote

import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Delivery receipts that could not be put on the wire, held until the socket comes back.
 *
 * Outbound *messages* have had this since the offline-queue work — [WsClient.queuePendingMessage]
 * writes them to SQLDelight and [WsClient.drainPendingMessages] replays them on reconnect. Receipts
 * had nothing: `send()` threw, the call site logged "best-effort, re-sent on the next incoming
 * message", and if no further message ever arrived it was simply never re-sent. The sender's ticks
 * then stayed wrong until one side restarted (#478).
 *
 * Deliberately **in memory**, unlike the message queue. A message the user typed must survive the
 * process; a read receipt must not outlive it, because the screen that would have produced it
 * re-sends one from its open-handler the moment it is opened again. Persisting receipts would buy a
 * SQLDelight table and a migration for a case the next chat open already covers.
 *
 * At most one entry per message id, holding the strongest status seen. A DELIVERED queued behind a
 * READ for the same message would otherwise undo it on the drain — the wire order is whatever the
 * loop happens to produce, and the server takes the last one it is told.
 */
class PendingAckQueue(private val maxSize: Int = DEFAULT_MAX_SIZE) {

    private val mutex = Mutex()

    /** Insertion-ordered, so the drain replays receipts in the order they were produced. */
    private val pending = LinkedHashMap<String, WsMessage.AckMessage>()

    /**
     * Queues [ack] for the next drain, replacing any weaker status already held for the same
     * message. A stronger status also moves the entry to the back of the queue, which keeps the
     * eviction below biased towards the receipts nobody has touched in a while.
     */
    suspend fun record(ack: WsMessage.AckMessage) {
        mutex.withLock {
            val existing = pending[ack.messageId]
            if (existing != null && rank(existing.status) >= rank(ack.status)) return@withLock
            pending.remove(ack.messageId)
            pending[ack.messageId] = ack
            while (pending.size > maxSize) {
                val oldest = pending.keys.firstOrNull() ?: break
                pending.remove(oldest)
            }
        }
    }

    /**
     * Removes and returns everything queued, oldest first.
     *
     * Take-then-send rather than send-while-holding: a send that fails must be able to [record]
     * itself again, and doing that under the same lock would deadlock.
     */
    suspend fun takeAll(): List<WsMessage.AckMessage> =
        mutex.withLock {
            val all = pending.values.toList()
            pending.clear()
            all
        }

    suspend fun size(): Int = mutex.withLock { pending.size }

    /**
     * Receipt strength. Only DELIVERED and READ ever reach the wire (see [WsMessage.AckMessage]);
     * the other two are listed so the `when` stays exhaustive if [MessageStatus] grows.
     */
    private fun rank(status: MessageStatus): Int =
        when (status) {
            MessageStatus.SENDING -> 0
            MessageStatus.SENT -> 1
            MessageStatus.DELIVERED -> 2
            MessageStatus.READ -> 3
        }

    private companion object {
        /**
         * A cap, not a target. Reached only by an app that stays offline through hundreds of
         * incoming messages, and in that case the oldest receipts are the ones the next chat open
         * will re-assert anyway.
         */
        const val DEFAULT_MAX_SIZE = 200
    }
}

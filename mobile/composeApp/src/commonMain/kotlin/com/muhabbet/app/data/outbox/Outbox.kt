package com.muhabbet.app.data.outbox

import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.AckStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The durable, ack-driven outbox (#792): the single owner of every message the user has sent and
 * the server has not yet confirmed.
 *
 * What it replaces was a fallback for "the socket is null", not an outbox. A message written to a
 * live socket was persisted nowhere and had no deadline, so a socket that died after the write lost
 * it; queued rows were deleted the moment they were written to the socket buffer, not when the
 * server acknowledged them; and after five reconnects a row was deleted with a log line. No failure
 * was representable, so the chat either deleted the bubble or showed a clock forever.
 *
 * Here, in order:
 * 1. [enqueue] persists the complete frame **before** anything touches the wire. If that fails the
 *    caller is told, because nothing will send it later.
 * 2. [drain] dispatches queued messages, oldest first, while a [Transport] is [attach]ed, and arms
 *    an ack deadline for each.
 * 3. Only [onServerAck] confirms a message, and only then does its row leave the store.
 * 4. A deadline that passes without an ack re-queues the message with a longer deadline; after
 *    [OutboxPolicy.maxAttempts] unanswered dispatches it becomes [OutboxState.Failed] — visible on
 *    [messages], persisted, and left only by [retry].
 * 5. [detach] (the socket went away) puts in-flight messages back in the queue without spending an
 *    attempt; the next [attach] and [drain] re-send them with the same messageId, and the server's
 *    `MSG_DUPLICATE` answer to a copy that had in fact landed is read as success.
 *
 * Every state change goes through the pure [reduce]; this class only sequences I/O around it. All
 * mutable state is touched under [mutex].
 */
class Outbox(
    private val store: OutboxStore?,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val policy: OutboxPolicy = OutboxPolicy()
) {

    /** Puts one frame on the wire, throwing if it could not be written. */
    fun interface Transport {
        suspend fun write(frame: WsMessage.SendMessage)
    }

    private val mutex = Mutex()
    private val _messages = MutableStateFlow<Map<String, OutgoingMessage>>(emptyMap())

    /** Every message this process knows about, by messageId. Confirmed ones are kept, bounded. */
    val messages: StateFlow<Map<String, OutgoingMessage>> = _messages

    private var loaded = false
    private var transport: Transport? = null
    private val timers = HashMap<String, Job>()

    init {
        // Load at once, so a chat opened straight after a restart sees its queued and failed
        // messages without waiting for the socket.
        scope.launch { mutex.withLock { ensureLoaded() } }
    }

    /** The outbox of one conversation, oldest first — what a chat merges into its message list. */
    fun forConversation(conversationId: String): Flow<List<OutgoingMessage>> =
        messages
            .map { all -> all.values.filter { it.conversationId == conversationId }.sortedBy { it.createdAtMs } }
            .distinctUntilChanged()

    /**
     * Accepts [frame] for delivery. Returns false only if it could not be persisted, in which case
     * nothing will ever send it and the caller must say so. Idempotent per messageId.
     */
    suspend fun enqueue(frame: WsMessage.SendMessage): Boolean {
        val accepted = mutex.withLock {
            ensureLoaded()
            if (_messages.value.containsKey(frame.messageId)) return@withLock true
            val message = OutgoingMessage(frame = frame, state = OutboxState.Queued, createdAtMs = clock())
            val stored = store != null && runCatchingCancellable { store.save(message) }
                .onFailure { e -> Log.e(TAG, "Could not persist ${frame.messageId}: ${e.message}") }
                .isSuccess
            if (stored) publish(message)
            stored
        }
        if (accepted) drain()
        return accepted
    }

    /**
     * A live, authenticated connection is available. Call [drain] next to send what is waiting —
     * separate so the connection actor can attach without itself waiting on socket writes.
     */
    suspend fun attach(transport: Transport) {
        mutex.withLock {
            ensureLoaded()
            this.transport = transport
        }
    }

    /** The connection is gone. In-flight messages go back to the queue at no cost in attempts. */
    suspend fun detach() {
        mutex.withLock {
            transport = null
            timers.values.forEach { it.cancel() }
            timers.clear()
            _messages.value.values
                .filter { it.state is OutboxState.Sending }
                .forEach { commit(it.reduce(OutboxEvent.ConnectionLost, policy)) }
        }
    }

    /** Dispatches queued messages, oldest first, until the queue is empty or a write fails. */
    suspend fun drain() {
        while (true) {
            val (message, wire) = mutex.withLock {
                val wire = transport ?: return
                val next = _messages.value.values
                    .filter { it.state == OutboxState.Queued }
                    .minByOrNull { it.createdAtMs }
                    ?: return
                val sending = next.reduce(
                    OutboxEvent.Dispatched(nowMs = clock(), ackTimeoutMs = policy.ackTimeoutFor(next.attempts)),
                    policy
                )
                commit(sending)
                sending to wire
            }
            val written = runCatchingCancellable { wire.write(message.frame) }
            if (written.isFailure) {
                Log.w(TAG, "Write failed for ${message.messageId}, back to the queue: ${written.exceptionOrNull()?.message}")
                detach()
                return
            }
            mutex.withLock { armDeadline(message) }
        }
    }

    /**
     * Applies the server's answer to a `message.send`. Returns false if the ack is for a message this
     * outbox does not hold (sent by an earlier process whose row already left, or not ours).
     */
    suspend fun onServerAck(ack: WsMessage.ServerAck): Boolean {
        val after = mutex.withLock {
            val current = _messages.value[ack.messageId] ?: return false
            val event = if (ack.status == AckStatus.OK) {
                OutboxEvent.Acked(ack.serverTimestamp)
            } else {
                OutboxEvent.Rejected(ack.errorCode)
            }
            timers.remove(ack.messageId)?.cancel()
            val next = current.reduce(event, policy)
            commit(next)
            if (next.state == OutboxState.Queued) scheduleRedrain(ack.messageId, next.attempts)
            next
        }
        Log.d(TAG, "Ack for ${ack.messageId}: ${after.state}")
        return true
    }

    /** A delivery receipt from the other side. Moves a held message forward, never back. */
    suspend fun onStatusUpdate(update: WsMessage.StatusUpdate) {
        if (update.status != MessageStatus.DELIVERED && update.status != MessageStatus.READ) return
        mutex.withLock {
            val current = _messages.value[update.messageId] ?: return
            commit(current.reduce(OutboxEvent.Receipt(update.status), policy))
        }
    }

    /** The user asked to try a failed message again. Same frame, same messageId. */
    suspend fun retry(messageId: String) {
        mutex.withLock {
            val current = _messages.value[messageId] ?: return
            commit(current.reduce(OutboxEvent.RetryRequested, policy))
        }
        drain()
    }

    // ─── Internals: all called with [mutex] held ───

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val rows = store?.let { s ->
            runCatchingCancellable { s.loadAll() }
                .onFailure { e -> Log.e(TAG, "Could not load the outbox: ${e.message}") }
                .getOrNull()
        }.orEmpty()
        if (rows.isEmpty()) return
        // A message that was in flight when the process died may or may not have landed. Sending
        // it again is safe — same messageId, so a copy that did land comes back MSG_DUPLICATE.
        val restored = rows.map { if (it.state is OutboxState.Sending) it.reduce(OutboxEvent.ConnectionLost, policy) else it }
        _messages.value = _messages.value + restored.associateBy { it.messageId }
        Log.d(TAG, "Restored ${restored.size} outbox message(s)")
    }

    private fun commit(message: OutgoingMessage) {
        store?.let { s ->
            runCatchingCancellable {
                if (message.state.isPersistent) s.save(message) else s.remove(message.messageId)
            }.onFailure { e -> Log.e(TAG, "Could not store ${message.messageId}: ${e.message}") }
        }
        publish(message)
    }

    private fun publish(message: OutgoingMessage) {
        val all = LinkedHashMap(_messages.value)
        all[message.messageId] = message
        // Confirmed messages stay visible on the flow so a chat can see Sent/Delivered/Read, but the
        // server owns them now: keep only the newest few hundred in memory.
        val confirmed = all.values.filter { it.state.isConfirmed }
        if (confirmed.size > MAX_CONFIRMED_RETAINED) {
            confirmed.sortedBy { it.createdAtMs }
                .take(confirmed.size - MAX_CONFIRMED_RETAINED)
                .forEach { all.remove(it.messageId) }
        }
        _messages.value = all
    }

    private fun armDeadline(message: OutgoingMessage) {
        val sending = message.state as? OutboxState.Sending ?: return
        // The ack may already have arrived between the write and now; then there is nothing to arm.
        if (_messages.value[message.messageId]?.state != sending) return
        timers.remove(message.messageId)?.cancel()
        timers[message.messageId] = scope.launch {
            delay((sending.deadlineMs - clock()).coerceAtLeast(0))
            val requeued = mutex.withLock {
                timers.remove(message.messageId)
                val current = _messages.value[message.messageId]
                if (current?.state != sending) return@launch
                val next = current.reduce(OutboxEvent.AckTimedOut, policy)
                Log.w(TAG, "No ack for ${message.messageId} (attempt ${sending.attempt}): ${next.state}")
                commit(next)
                next.state == OutboxState.Queued
            }
            if (requeued) drain()
        }
    }

    /** A transient refusal (rate limited): wait out the same growing deadline before retrying. */
    private fun scheduleRedrain(messageId: String, attempts: Int) {
        timers.remove(messageId)?.cancel()
        timers[messageId] = scope.launch {
            delay(policy.ackTimeoutFor(attempts))
            mutex.withLock { timers.remove(messageId) }
            drain()
        }
    }

    private companion object {
        const val TAG = "Outbox"
        const val MAX_CONFIRMED_RETAINED = 200
    }
}

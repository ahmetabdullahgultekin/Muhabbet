package com.muhabbet.app.data.outbox

import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.model.advance
import com.muhabbet.shared.protocol.WsMessage

/**
 * One message the user sent, and how far it has got (#792, ADR 0012).
 *
 * [frame] is the complete `message.send` frame exactly as it was composed — never a projection of
 * it. The offline queue this replaces stored eight hand-picked columns and rebuilt the frame from
 * them on reconnect, which silently dropped `scheduledAt`, `viewOnce`, `mediaId`, `thumbnailUrl` and
 * `forwardedFrom` (#795). Re-sending [frame] unchanged, with the same `messageId`, is also what makes
 * a retry idempotent: the server answers a second copy with `MSG_DUPLICATE`, which [reduce] treats
 * as proof that the first one landed.
 *
 * [attempts] counts dispatches that went unanswered on a live connection. A connection dropping
 * under an in-flight message does not count, so flaky reconnects alone can never exhaust a message —
 * the old queue bumped its counter once per reconnect and threw the message away after five.
 */
data class OutgoingMessage(
    val frame: WsMessage.SendMessage,
    val state: OutboxState,
    val attempts: Int = 0,
    val createdAtMs: Long
) {
    val messageId: String get() = frame.messageId
    val conversationId: String get() = frame.conversationId
}

/** The outbox lifecycle. Pure data; [reduce] is the only thing that moves between these. */
sealed interface OutboxState {
    /** Persisted, waiting for a live connection. */
    data object Queued : OutboxState

    /** On the wire, waiting for the server's ack until [deadlineMs]. */
    data class Sending(val attempt: Int, val deadlineMs: Long) : OutboxState

    /** The server has the message. From here it can no longer fail. */
    data class Sent(val serverTimestamp: Long?) : OutboxState

    data object Delivered : OutboxState
    data object Read : OutboxState

    /** The outbox stopped trying. Survives a restart, and [OutboxEvent.RetryRequested] leaves it. */
    data class Failed(val failure: SendFailure) : OutboxState
}

/** Why a message ended in [OutboxState.Failed]. */
sealed interface SendFailure {
    /** The server refused it with [code] — too long, not a member, announcement-only, … */
    data class Rejected(val code: String?) : SendFailure

    /** No acknowledgement after [OutboxPolicy.maxAttempts] dispatches on a live connection. */
    data object Exhausted : SendFailure
}

/** Everything that can happen to an outgoing message. */
sealed interface OutboxEvent {
    data class Dispatched(val nowMs: Long, val ackTimeoutMs: Long) : OutboxEvent
    data class Acked(val serverTimestamp: Long?) : OutboxEvent
    data class Rejected(val code: String?) : OutboxEvent
    data object AckTimedOut : OutboxEvent
    data object ConnectionLost : OutboxEvent
    data object RetryRequested : OutboxEvent

    /** A delivery receipt from the recipient's side: DELIVERED or READ. */
    data class Receipt(val status: MessageStatus) : OutboxEvent
}

/** Limits the reducer applies. Values live here, not at call sites. */
data class OutboxPolicy(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val baseAckTimeoutMs: Long = DEFAULT_ACK_TIMEOUT_MS,
    val maxAckTimeoutMs: Long = MAX_ACK_TIMEOUT_MS
) {
    /** Ack deadline for the next dispatch: doubles per unanswered attempt, capped. */
    fun ackTimeoutFor(attempts: Int): Long =
        (baseAckTimeoutMs shl attempts.coerceIn(0, MAX_SHIFT)).coerceAtMost(maxAckTimeoutMs)

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val DEFAULT_ACK_TIMEOUT_MS = 15_000L
        const val MAX_ACK_TIMEOUT_MS = 120_000L
        private const val MAX_SHIFT = 10

        /** The server already holds this messageId: an earlier copy landed. */
        const val CODE_DUPLICATE = "MSG_DUPLICATE"

        /** Refusals that say "not now" rather than "never": retried, counted against the budget. */
        val TRANSIENT_CODES = setOf("RATE_LIMITED", "INTERNAL_ERROR")
    }
}

/**
 * The transition function. Total and pure: every (state, event) pair has an answer, and an event
 * that does not apply to a state leaves it unchanged rather than throwing.
 *
 * Once the server has confirmed a message (Sent, Delivered, Read) only receipts move it, and only
 * forward — the same lattice as [MessageStatus.advance], so the outbox and the chat cannot disagree.
 */
fun OutgoingMessage.reduce(event: OutboxEvent, policy: OutboxPolicy = OutboxPolicy()): OutgoingMessage {
    val confirmed = state.isConfirmed
    return when (event) {
        is OutboxEvent.Dispatched -> when (state) {
            OutboxState.Queued -> copy(
                state = OutboxState.Sending(attempt = attempts + 1, deadlineMs = event.nowMs + event.ackTimeoutMs)
            )
            else -> this
        }
        is OutboxEvent.Acked ->
            if (confirmed) this else copy(state = OutboxState.Sent(event.serverTimestamp))
        is OutboxEvent.Rejected -> when {
            confirmed -> this
            event.code == OutboxPolicy.CODE_DUPLICATE -> copy(state = OutboxState.Sent(null))
            event.code in OutboxPolicy.TRANSIENT_CODES -> unanswered(policy)
            else -> copy(state = OutboxState.Failed(SendFailure.Rejected(event.code)))
        }
        OutboxEvent.AckTimedOut -> if (state is OutboxState.Sending) unanswered(policy) else this
        OutboxEvent.ConnectionLost -> if (state is OutboxState.Sending) copy(state = OutboxState.Queued) else this
        OutboxEvent.RetryRequested ->
            if (state is OutboxState.Failed) copy(state = OutboxState.Queued, attempts = 0) else this
        is OutboxEvent.Receipt -> {
            if (event.status != MessageStatus.DELIVERED && event.status != MessageStatus.READ) return this
            val next = state.toMessageStatus().advance(event.status)
            if (next == state.toMessageStatus()) this else copy(state = next.toConfirmedState(state))
        }
    }
}

/** A dispatch that drew no usable answer: back in the queue, or out of budget. */
private fun OutgoingMessage.unanswered(policy: OutboxPolicy): OutgoingMessage {
    val spent = attempts + 1
    return if (spent >= policy.maxAttempts) {
        copy(state = OutboxState.Failed(SendFailure.Exhausted), attempts = spent)
    } else {
        copy(state = OutboxState.Queued, attempts = spent)
    }
}

private fun MessageStatus.toConfirmedState(current: OutboxState): OutboxState = when (this) {
    MessageStatus.DELIVERED -> OutboxState.Delivered
    MessageStatus.READ -> OutboxState.Read
    MessageStatus.SENT -> current as? OutboxState.Sent ?: OutboxState.Sent(null)
    MessageStatus.SENDING, MessageStatus.FAILED -> current
}

/** True once the server has the message. */
val OutboxState.isConfirmed: Boolean
    get() = this is OutboxState.Sent || this == OutboxState.Delivered || this == OutboxState.Read

/** True for the states that must survive a process death. Confirmed messages leave the table. */
val OutboxState.isPersistent: Boolean get() = !isConfirmed

/** How a bubble renders this state, on the shared delivery lattice. */
fun OutboxState.toMessageStatus(): MessageStatus = when (this) {
    OutboxState.Queued, is OutboxState.Sending -> MessageStatus.SENDING
    is OutboxState.Sent -> MessageStatus.SENT
    OutboxState.Delivered -> MessageStatus.DELIVERED
    OutboxState.Read -> MessageStatus.READ
    is OutboxState.Failed -> MessageStatus.FAILED
}

package com.muhabbet.shared.model

/**
 * The delivery lifecycle as a monotone state machine, shared by every place that changes a status.
 *
 * Status used to be assigned: a ServerAck set SENT, a StatusUpdate copied its own value over the
 * message's. Events arrive in whatever order the offline queue, a reconnect and the server's
 * fan-out produce, so a late SENT ack regressed a message that was already READ and a late
 * DELIVERED turned blue ticks grey again (#879). Assignment cannot be made order-independent;
 * a join on a lattice can.
 *
 * The lattice is SENDING/FAILED < SENT < DELIVERED < READ. [advance] is the join: applying any set
 * of server-sourced events in any order ends at the strongest of them, and nothing a server says
 * can move a message backwards. FAILED is the one local, non-monotone exit — it can only be entered
 * from an unconfirmed state and only be left by [retry] or by proof of receipt.
 */

/** Position on the delivery lattice. SENDING and FAILED share the bottom: neither is confirmed. */
val MessageStatus.deliveryRank: Int
    get() = when (this) {
        MessageStatus.SENDING, MessageStatus.FAILED -> 0
        MessageStatus.SENT -> 1
        MessageStatus.DELIVERED -> 2
        MessageStatus.READ -> 3
    }

/** True once the server has taken the message — the point after which it can no longer fail. */
val MessageStatus.isServerConfirmed: Boolean get() = deliveryRank > 0

/**
 * The status after learning [to], never weaker than the current one.
 *
 * - A server-sourced status (SENT, DELIVERED, READ) wins only if it is stronger: this is a max.
 * - FAILED is accepted only while the message is unconfirmed. A failure reported after the server
 *   already acknowledged the message is a stale timeout racing the ack, and the ack is the truth.
 * - SENDING never overrides FAILED: leaving FAILED is the user's decision, through [retry].
 */
fun MessageStatus.advance(to: MessageStatus): MessageStatus = when {
    to == MessageStatus.FAILED -> if (isServerConfirmed) this else MessageStatus.FAILED
    to == MessageStatus.SENDING -> this
    to.deliveryRank > deliveryRank -> to
    else -> this
}

/** The explicit way out of FAILED: back into the outbox. Every other status is left alone. */
fun MessageStatus.retry(): MessageStatus =
    if (this == MessageStatus.FAILED) MessageStatus.SENDING else this

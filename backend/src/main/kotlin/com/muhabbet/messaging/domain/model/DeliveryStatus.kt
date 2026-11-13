package com.muhabbet.messaging.domain.model

import java.util.UUID

/**
 * One recipient's progress through a message's lifecycle: `SENT -> DELIVERED -> READ` (#870).
 *
 * **Declaration order is the lifecycle order**, and every rule below is derived from it rather than
 * restated: [advance] compares, [advancesFrom] filters, [aggregate] takes the minimum. Inserting a
 * constant anywhere but at its true place in the lifecycle changes all three at once, which is the
 * point — there is one ordering, not three hand-maintained ones.
 *
 * The lifecycle used to exist only as an `if` inside the persistence adapter (`READ` must not be
 * overwritten by anything else), applied as a read-then-save with nothing to stop a concurrent
 * writer committing between the read and the save. The rule now lives here, as a pure function,
 * and the store applies the very same predicate atomically through [advancesFrom].
 */
enum class DeliveryStatus {
    SENT,
    DELIVERED,
    READ;

    /**
     * The transition a receipt for [to] causes when this is the stored state.
     *
     * Monotone: a state only ever moves forward. A receipt for the current state or an earlier one
     * is [DeliveryTransition.Ignored] — that is what makes acks idempotent (the same receipt twice)
     * and order-insensitive (a slow DELIVERED arriving after READ).
     */
    fun advance(to: DeliveryStatus): DeliveryTransition =
        if (to > this) DeliveryTransition.Applied(from = this, to = to) else DeliveryTransition.Ignored(current = this)

    /**
     * Every stored state from which a receipt for this state is [DeliveryTransition.Applied].
     *
     * This is [advance] turned into a set, so the persistence adapter can push the transition rule
     * into the `WHERE` clause of a single conditional `UPDATE` (`status IN advancesFrom()`). The
     * database then re-checks it against the committed row under the row lock, which is what makes
     * two racing receipts safe; a read in Kotlin followed by a save never could be.
     */
    fun advancesFrom(): Set<DeliveryStatus> = entries.filter { it.advance(this) is DeliveryTransition.Applied }.toSet()

    companion object {
        /**
         * What the **sender** of a message sees, given every recipient's state (#871).
         *
         * The minimum across recipients: a message is DELIVERED once it has reached *everyone* and
         * READ once *everyone* has read it — the convention every mainstream messenger uses for the
         * bubble's tick, and the only one under which the tick says something true about the
         * group. "Any recipient DELIVERED" (what the history path used to compute) told a sender of
         * a ten-person group that their message had arrived when one phone had it.
         *
         * No recipients — a group the sender is alone in — is [SENT]: the server has it, and there
         * is nobody for it to reach.
         *
         * Monotone in each argument, so as long as each recipient's state only advances (which
         * [advance] guarantees), the aggregate only advances too.
         */
        fun aggregate(perRecipient: Collection<DeliveryStatus>): DeliveryStatus = perRecipient.minOrNull() ?: SENT
    }
}

/** The result of [DeliveryStatus.advance]: the stored state either moves forward or stays put. */
sealed interface DeliveryTransition {
    data class Applied(val from: DeliveryStatus, val to: DeliveryStatus) : DeliveryTransition
    data class Ignored(val current: DeliveryStatus) : DeliveryTransition
}

/**
 * The state of this row that may be shown to someone other than the recipient it belongs to.
 *
 * A reader who has turned read receipts off still has their row stored as READ — that row is what
 * clears their own unread badge — but nobody else is told: READ is published as DELIVERED. The
 * sender learns the message arrived, never that it was opened. One column serves both concerns,
 * which is why the downgrade happens here, at publish time, and never at storage time.
 */
fun MessageDeliveryStatus.publishedStatus(receiptsDisabled: Set<UUID>): DeliveryStatus =
    if (status == DeliveryStatus.READ && userId in receiptsDisabled) DeliveryStatus.DELIVERED else status

/**
 * The sender's tick for one message, from its recipients' rows: each row's [publishedStatus], then
 * [DeliveryStatus.aggregate]. The live WebSocket path and the REST history path both call this, so
 * the tick a sender sees live is the tick they see after reloading (#871).
 */
fun senderView(rows: Collection<MessageDeliveryStatus>, receiptsDisabled: Set<UUID>): DeliveryStatus =
    DeliveryStatus.aggregate(rows.map { it.publishedStatus(receiptsDisabled) })

/**
 * A receipt a recipient sends for messages they were sent.
 *
 * Sealed rather than a bare [DeliveryStatus] because the two receipts are different operations,
 * not one operation with a parameter: DELIVERED names one message, READ names a point in a
 * conversation up to which everything has been seen. And SENT is not a receipt at all — it is what
 * the server records on send — so it has no constructor here and cannot be acknowledged.
 */
sealed interface Receipt {
    val readerId: UUID

    /** [messageId] reached one of [readerId]'s devices. */
    data class Delivered(val messageId: UUID, override val readerId: UUID) : Receipt

    /**
     * [readerId] has seen [conversationId] up to and including [upToMessageId]: every message in it
     * sent by someone else at or before that one's server timestamp is read. Messages after it are
     * untouched, so a READ receipt for an older message can never mark newer, unseen ones read.
     */
    data class Read(val conversationId: UUID, val upToMessageId: UUID, override val readerId: UUID) : Receipt
}

/**
 * One recipient row a receipt actually moved forward — returned by the store only for rows whose
 * stored state changed, so a caller publishes exactly the transitions that happened and nothing a
 * late or repeated receipt would otherwise have re-announced.
 */
data class ReceiptAdvance(val messageId: UUID, val conversationId: UUID, val senderId: UUID)

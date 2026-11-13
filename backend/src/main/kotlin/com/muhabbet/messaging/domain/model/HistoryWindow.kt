package com.muhabbet.messaging.domain.model

import java.time.Instant

/**
 * The part of a conversation's history one member may read (#874).
 *
 * Read authorization used to be a boolean — "is a member" — so anyone added to a group, or joining
 * through an invite link, read everything written before they arrived, and a member removed and
 * re-added read everything said while they were out. Membership is an interval, not a flag, and
 * this is the domain's name for the lower end of it.
 *
 * Sealed rather than a nullable `Instant` so that every read port has to be handed a decision. A
 * `null` would read the same whether a caller chose "no bound" or simply forgot to ask; with this
 * type a new query cannot be written against the out-port without someone writing [Unbounded] on
 * purpose.
 */
sealed interface HistoryWindow {

    /** True when [message] falls inside the window. */
    fun admits(message: Message): Boolean

    /** The whole history: direct conversations and channels. */
    data object Unbounded : HistoryWindow {
        override fun admits(message: Message): Boolean = true
    }

    /**
     * Messages stamped at or after [visibleFrom]. Inclusive, because a member is added before the
     * first message they are meant to see is stamped — the boundary instant belongs to them.
     */
    data class From(val visibleFrom: Instant) : HistoryWindow {
        override fun admits(message: Message): Boolean = !message.serverTimestamp.isBefore(visibleFrom)
    }

    companion object {
        /**
         * The policy, in one place.
         *
         * - **GROUP** — from the moment this membership began. WhatsApp and Signal private groups
         *   behave this way; it is also what KVKK purpose limitation asks of a group whose members
         *   change. A re-added member has a new row with a new `joinedAt` (removal deletes the row),
         *   so the gap they were absent for stays closed without any extra bookkeeping.
         * - **DIRECT** — both members exist from the conversation's creation, so a bound would
         *   change nothing except to make the rule harder to state. Unbounded.
         * - **CHANNEL** — a broadcast that subscribers are expected to scroll back through, as on
         *   every channel product. Unbounded.
         *
         * A group setting to share history with new members would be one more branch here and
         * nowhere else, which is the reason the decision is a function rather than a query clause.
         */
        fun of(type: ConversationType, member: ConversationMember): HistoryWindow = when (type) {
            ConversationType.GROUP -> From(member.joinedAt)
            ConversationType.DIRECT, ConversationType.CHANNEL -> Unbounded
        }
    }
}

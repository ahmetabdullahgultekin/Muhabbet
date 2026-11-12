package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * Hands out the next position in a conversation (#750, ADR 0014).
 *
 * One counter per conversation (`conversations.last_seq`) serves both a new message's `seq` and the
 * `change_seq` an edit or a delete stamps on an existing row, so every event in a conversation has
 * one total order.
 *
 * **Must be called inside the caller's transaction, as late as possible before the write it
 * numbers.** The allocation is a row-level `UPDATE` on the conversation, and the lock it takes is
 * held until that transaction commits. That lock is the whole guarantee: two concurrent sends to one
 * conversation serialise on it, so values are handed out without gaps or duplicates and become
 * visible in the order they were allocated — a reader can never see `n + 1` committed while `n` is
 * still in flight. A rollback releases the value with the lock, so no number is ever spent on a
 * message that does not exist. The price is that sends to the *same* conversation queue behind each
 * other for the rest of the transaction, which is why the call belongs right before the insert and
 * not at the top of the method.
 */
interface ConversationSequencePort {

    /**
     * Allocates and returns the next value for [conversationId]. Throws if no transaction is
     * active, or if the conversation does not exist.
     */
    fun next(conversationId: UUID): Long
}

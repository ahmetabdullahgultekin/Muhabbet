package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Reaction
import java.util.UUID

interface ReactionRepository {
    /**
     * Stores [reaction] unless the same (message, user, emoji) is already there, and reports
     * whether a row was written (#783, #336).
     *
     * One atomic statement rather than look-then-insert: two identical requests in flight — a
     * double tap, a retry after a timeout — both saw "absent", and the second insert hit the unique
     * constraint as a 500, which is exactly the duplicate the look was meant to prevent.
     */
    fun addIfAbsent(reaction: Reaction): Boolean
    fun findByMessageId(messageId: UUID): List<Reaction>

    /** Every reaction on any of [messageIds], in one query — for a history page (#783). */
    fun findByMessageIds(messageIds: Collection<UUID>): List<Reaction>
    fun deleteByMessageIdAndUserIdAndEmoji(messageId: UUID, userId: UUID, emoji: String)
}

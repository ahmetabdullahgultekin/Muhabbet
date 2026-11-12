package com.muhabbet.shared

import com.muhabbet.messaging.domain.port.out.ConversationSequencePort
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The in-memory stand-in for `conversations.last_seq`: one counter per conversation, starting at 1.
 *
 * It is the behaviour the real adapter promises (gapless, per conversation) without the row lock,
 * which unit tests do not need — the lock is asserted against a real database in
 * `ConversationSequenceIntegrationTest`.
 */
class InMemoryConversationSequence : ConversationSequencePort {
    private val counters = ConcurrentHashMap<UUID, Long>()

    override fun next(conversationId: UUID): Long = counters.merge(conversationId, 1L) { a, b -> a + b } ?: 1L

    /** The last value handed out for [conversationId], 0 if none. */
    fun lastFor(conversationId: UUID): Long = counters[conversationId] ?: 0L
}

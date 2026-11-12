package com.muhabbet.app.data.local

/**
 * The part of the offline cache that belongs to whoever is signed in, as one thing to erase.
 *
 * Extracted for the same reason as [ConversationCache], [MessageCache] and [PendingMessageCache]:
 * [LocalCache] needs a SQLDelight driver and an Android `Context`, so the logout path could only be
 * tested on a device if it named the concrete class. The three `clear*` queries had existed for
 * months with no caller at all — so a logout left every conversation, message and unsent outbox
 * row on disk in plaintext, readable by the next account to sign in on the phone (#752).
 */
interface SessionDataCache {
    /** Conversations, messages and the unsent outbox, all at once or not at all. */
    fun clearSessionData()
}

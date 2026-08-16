package com.muhabbet.app.data.local

import com.muhabbet.shared.dto.ConversationResponse

/**
 * The conversation half of the offline cache, as [com.muhabbet.app.data.repository.ConversationRepository]
 * needs it — and only that half.
 *
 * Extracted so the repository can be built in a test. [LocalCache] is backed by SQLDelight and its
 * driver needs an Android `Context`, so depending on the concrete class made every conversation
 * path — contact sync, opening a chat, deleting one — reachable only from a running app on a
 * device, and this host has no emulator and cannot have one. The four methods below are exactly
 * what the repository calls; [LocalCache] implements them unchanged and every other caller still
 * depends on the concrete class.
 */
interface ConversationCache {
    fun getConversations(): List<ConversationResponse>
    fun upsertConversation(conv: ConversationResponse)
    fun upsertConversations(conversations: List<ConversationResponse>)
    fun deleteConversation(id: String)
}

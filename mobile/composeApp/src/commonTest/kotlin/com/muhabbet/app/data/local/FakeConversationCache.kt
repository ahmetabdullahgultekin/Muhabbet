package com.muhabbet.app.data.local

import com.muhabbet.shared.dto.ConversationResponse

/**
 * In-memory stand-in for the SQLDelight cache, so repository tests can run without a device.
 *
 * Mirrors [FakeTokenStorage]: real behaviour, no platform driver.
 *
 * The backing list is named `stored` rather than `conversations` because a public property of that
 * name compiles to `getConversations()` on the JVM and clashes with the interface method.
 */
class FakeConversationCache : ConversationCache {

    val stored = mutableListOf<ConversationResponse>()

    override fun getConversations(): List<ConversationResponse> = stored.toList()

    override fun upsertConversation(conv: ConversationResponse) {
        stored.removeAll { it.id == conv.id }
        stored += conv
    }

    override fun upsertConversations(conversations: List<ConversationResponse>) {
        conversations.forEach(::upsertConversation)
    }

    override fun deleteConversation(id: String) {
        stored.removeAll { it.id == id }
    }
}

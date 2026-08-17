package com.muhabbet.app.data.local

import com.muhabbet.shared.model.Message

/**
 * In-memory stand-in for the SQLDelight message cache, so repository tests can run without a
 * device. Mirrors [FakeConversationCache]: real behaviour, no platform driver.
 */
class FakeMessageCache : MessageCache {

    val stored = mutableListOf<Message>()

    override fun getMessagesByPage(conversationId: String, limit: Int): List<Message> =
        stored.filter { it.conversationId == conversationId }.take(limit)

    override fun upsertMessages(messages: List<Message>) {
        messages.forEach { message ->
            stored.removeAll { it.id == message.id }
            stored += message
        }
    }
}

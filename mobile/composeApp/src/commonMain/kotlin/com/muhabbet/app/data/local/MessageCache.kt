package com.muhabbet.app.data.local

import com.muhabbet.shared.model.Message

/**
 * The message half of the offline cache, as [com.muhabbet.app.data.repository.MessageRepository]
 * needs it — and only that half.
 *
 * The same extraction, and for the same reason, as [ConversationCache]: [LocalCache] is backed by
 * SQLDelight and its driver wants an Android `Context`, so a repository that names the concrete
 * class can only be exercised on a device — and this host has no emulator and cannot have one. The
 * two methods below are exactly what the repository calls. [LocalCache] implements them unchanged
 * and every other caller still depends on the concrete class.
 */
interface MessageCache {
    fun getMessagesByPage(conversationId: String, limit: Int): List<Message>
    fun upsertMessages(messages: List<Message>)
}

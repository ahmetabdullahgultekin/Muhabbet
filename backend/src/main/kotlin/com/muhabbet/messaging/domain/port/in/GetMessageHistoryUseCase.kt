package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Message
import java.time.Instant
import java.util.UUID

/**
 * Reading a conversation's messages: a page of history, its media, and the background-sync catch-up.
 *
 * Delivery-status resolution and per-message info were on this interface too and have their own
 * ports now ([ResolveDeliveryStatusUseCase], [GetMessageInfoUseCase]) — they are receipt questions,
 * answered by the receipt service, and a caller that only pages history no longer depends on them
 * (#894).
 */
interface GetMessageHistoryUseCase {
    fun getMessages(
        conversationId: UUID,
        userId: UUID,
        cursor: String?,
        limit: Int,
        direction: String = "before"
    ): MessagePage

    fun getMediaMessages(conversationId: UUID, userId: UUID, limit: Int, offset: Int): List<Message>

    /**
     * Returns all messages across user's conversations since a given timestamp.
     * Used by background sync to catch up on missed messages.
     */
    fun getMessagesSince(userId: UUID, since: Instant): List<Message>
}

data class MessagePage(
    val items: List<Message>,
    val nextCursor: String?,
    val hasMore: Boolean
)

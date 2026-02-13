package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

interface GetMessageHistoryUseCase {
    fun getMessages(
        conversationId: UUID,
        userId: UUID,
        cursor: String?,
        limit: Int,
        direction: String = "before"
    ): MessagePage

    /**
     * Resolves the aggregate delivery status for a list of messages from the perspective of the requesting user.
     * - Sender perspective: aggregate = min across all recipients (all READ → READ, any DELIVERED/READ → DELIVERED, else SENT)
     * - Recipient perspective: their own status row
     */
    fun resolveDeliveryStatuses(messages: List<Message>, requestingUserId: UUID): Map<UUID, DeliveryStatus>

    fun getMediaMessages(conversationId: UUID, userId: UUID, limit: Int, offset: Int): List<Message>
}

data class MessagePage(
    val items: List<Message>,
    val nextCursor: String?,
    val hasMore: Boolean
)

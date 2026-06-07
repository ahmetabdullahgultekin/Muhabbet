package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import java.time.Instant
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

    /**
     * Returns all messages across user's conversations since a given timestamp.
     * Used by background sync to catch up on missed messages.
     */
    fun getMessagesSince(userId: UUID, since: Instant): List<Message>

    /**
     * Returns a message plus its per-recipient delivery statuses, AFTER authorizing that the
     * requesting user is a member of the message's conversation. Throws MSG_NOT_MEMBER otherwise.
     * Closes the getMessageInfo IDOR — the membership check lives here, not in the controller.
     */
    fun getMessageInfo(messageId: UUID, requesterId: UUID): MessageInfo
}

data class MessagePage(
    val items: List<Message>,
    val nextCursor: String?,
    val hasMore: Boolean
)

data class MessageInfo(
    val message: Message,
    val deliveryStatuses: List<MessageDeliveryStatus>
)

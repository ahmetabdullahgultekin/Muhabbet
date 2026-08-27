package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
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
     * Returns a message plus its resolved recipients, AFTER authorizing that the requesting user is
     * a member of the message's conversation. Throws MSG_NOT_MEMBER otherwise.
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
    /** Everyone the message was delivered to, excluding the sender, with names already resolved. */
    val recipients: List<MessageRecipient>
)

data class MessageRecipient(
    val userId: UUID,
    val displayName: String?,
    val avatarUrl: String?,
    val status: DeliveryStatus,
    /**
     * Null when [status] was downgraded because the recipient turned read receipts off. The row
     * carries a single `updated_at`, which a READ overwrites with the moment the message was
     * opened — publishing it beside a DELIVERED would hand the sender the read time under another
     * label, which is the very thing the downgrade exists to withhold (#620).
     */
    val updatedAt: Instant?
)

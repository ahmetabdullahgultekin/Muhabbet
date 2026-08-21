package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import java.time.Instant
import java.util.UUID

interface MessageRepository {
    fun save(message: Message): Message
    fun findById(id: UUID): Message?
    fun existsById(id: UUID): Boolean
    fun findByConversationId(
        conversationId: UUID,
        before: Instant?,
        limit: Int
    ): List<Message>

    fun findUndeliveredForUser(userId: UUID, since: Instant?): List<Message>
    /**
     * Every recipient's row for one message, in one call.
     *
     * There is deliberately no single-row version. This replaced one, and writing the rows one at a
     * time from inside a loop is exactly what made a group message cost a statement per member on
     * the hottest path in the app (#492) — leaving the per-row method in place would leave the way
     * back to it. A caller with one row passes a list of one.
     */
    fun saveDeliveryStatuses(statuses: List<MessageDeliveryStatus>)
    fun updateDeliveryStatus(messageId: UUID, userId: UUID, status: DeliveryStatus)
    fun markConversationRead(conversationId: UUID, userId: UUID)
    fun getUnreadCount(conversationId: UUID, userId: UUID): Int
    fun getLastMessage(conversationId: UUID): Message?

    // Batch operations for inbox optimization (avoids N+1 queries)
    fun getLastMessages(conversationIds: List<UUID>): Map<UUID, Message>
    fun getUnreadCounts(conversationIds: List<UUID>, userId: UUID): Map<UUID, Int>

    // Message management
    fun softDelete(messageId: UUID)
    fun updateContent(messageId: UUID, newContent: String, editedAt: Instant)

    // Media count
    fun countMediaInConversation(conversationId: UUID): Int

    // Batch delivery status lookup
    fun getDeliveryStatuses(messageIds: List<UUID>): List<MessageDeliveryStatus>

    // Media messages for shared media screen
    fun findMediaByConversationId(conversationId: UUID, limit: Int, offset: Int): List<Message>

    // Search — membership-scoped: results are restricted to conversations the requesting user belongs to.
    fun searchInConversation(conversationId: UUID, userId: UUID, query: String, limit: Int, offset: Int): List<Message>
    fun searchGlobal(userId: UUID, query: String, limit: Int, offset: Int): List<Message>

    // View-Once

    /**
     * Burns a view-once message, returning the number of rows it actually changed.
     *
     * The count is the concurrency control and the only reason this is not a `Unit`. The underlying
     * statement is conditional on `view_once = true AND viewed_at IS NULL`, so two taps arriving
     * together both pass the service's read-side checks and exactly one of them updates a row. The
     * loser gets 0 back and is refused — without the count both would be told they had won and the
     * media would be released twice.
     */
    fun markViewOnceViewed(messageId: UUID, viewedBy: UUID, viewedAt: Instant): Int

    // Scheduled messages
    /**
     * Due scheduled messages, oldest first, at most [limit] of them.
     *
     * Bounded by contract rather than by the adapter, so the caller owns how much work one run
     * takes on. Unbounded, a backlog arrives as one list and — before #560 — as one transaction.
     */
    fun findScheduledMessagesReadyToSend(now: Instant, limit: Int): List<Message>
    fun markAsDelivered(messageId: UUID)
}

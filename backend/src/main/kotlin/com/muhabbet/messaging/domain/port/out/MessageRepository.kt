package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import java.time.Instant
import java.util.UUID

/**
 * **Reads that take a `viewerId` hide unreleased scheduled messages from everyone but their sender
 * (#746).** A scheduled message is written when it is scheduled, so any read of another user's
 * messages has to answer "is this one due yet" — the viewer is how it does.
 */
interface MessageRepository {
    /**
     * Inserts a new message. Throws `BusinessException(MSG_DUPLICATE)` when a row with the same id
     * already exists — the concurrent-retry case the service's own lookup cannot see, which it
     * answers by looking again in a fresh transaction and replaying the original ack (#750).
     */
    fun save(message: Message): Message
    fun findById(id: UUID): Message?
    fun existsById(id: UUID): Boolean
    fun findByConversationId(
        conversationId: UUID,
        viewerId: UUID,
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

    /**
     * Moves [userId]'s row for [messageId] forward to [status], never backwards, and reports
     * whether anything changed. False for a row already at or past [status] and for a row that
     * does not exist — and false is what tells the caller there is nothing to broadcast (#299).
     */
    fun updateDeliveryStatus(messageId: UUID, userId: UUID, status: DeliveryStatus): Boolean
    fun markConversationRead(conversationId: UUID, userId: UUID)
    fun getUnreadCount(conversationId: UUID, userId: UUID): Int
    fun getLastMessage(conversationId: UUID, viewerId: UUID): Message?

    // Batch operations for inbox optimization (avoids N+1 queries)
    fun getLastMessages(conversationIds: List<UUID>, viewerId: UUID): Map<UUID, Message>
    fun getUnreadCounts(conversationIds: List<UUID>, userId: UUID): Map<UUID, Int>

    // Message management

    /**
     * Soft-deletes a message. [changeSeq] is the value allocated for the delete as an event in the
     * conversation's order (#750); null only for a row that never had a place in it — a scheduled
     * message dropped before release.
     */
    fun softDelete(messageId: UUID, changeSeq: Long?)
    fun updateContent(messageId: UUID, newContent: String, editedAt: Instant, changeSeq: Long)

    /**
     * Edits / soft-deletes a message only while it is still an unreleased scheduled row, and
     * reports whether it was. Such a row has no place in the conversation's order yet, so no
     * change_seq is stamped — it gets `change_seq = seq` when it is released. False means the
     * scheduler released it first; the caller then treats it as a live message.
     */
    fun updateScheduledContent(messageId: UUID, newContent: String, editedAt: Instant): Boolean
    fun softDeleteScheduled(messageId: UUID): Boolean

    // Media count
    fun countMediaInConversation(conversationId: UUID, viewerId: UUID): Int

    // Batch delivery status lookup
    fun getDeliveryStatuses(messageIds: List<UUID>): List<MessageDeliveryStatus>

    // Media messages for shared media screen
    fun findMediaByConversationId(conversationId: UUID, viewerId: UUID, limit: Int, offset: Int): List<Message>

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

    /**
     * Releases a due scheduled message with its allocated [seq], as of [releasedAt]. False when it
     * was no longer scheduled — another run released it first — so the caller can roll back.
     */
    fun markAsDelivered(messageId: UUID, seq: Long, releasedAt: Instant): Boolean

    // Disappearing messages

    /**
     * Disappearing messages whose deadline has passed and which are not already deleted, oldest
     * first, at most [limit] of them.
     *
     * Bounded for the same reason [findScheduledMessagesReadyToSend] is: the sweep runs on a
     * `fixedDelay` schedule, so an unbounded backlog turns one run into a long one and delays every
     * run behind it. The leftovers are picked up by the next sweep in the same order.
     *
     * On the out-port rather than only on the Spring Data interface because the sweep now has to do
     * more than mutate rows — it has to tell the connected members, which is domain work. The job
     * that used to reach straight into the JPA repository from `adapter.in.scheduler` could not.
     */
    fun findExpiredMessages(now: Instant, limit: Int): List<Message>

    /**
     * Marks [messageIds] deleted as of [deletedAt], returning how many rows actually changed.
     *
     * One statement for the batch, not one per message: the sweep can legitimately have a few
     * hundred rows due at once, and [softDelete] in a loop is how a background job quietly becomes
     * the most expensive thing on the database.
     */
    fun softDeleteExpired(messageIds: List<UUID>, deletedAt: Instant): Int
}

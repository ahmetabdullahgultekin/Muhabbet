package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.HistoryPosition
import com.muhabbet.messaging.domain.model.HistoryWindow
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import java.time.Instant
import java.util.UUID

interface MessageRepository {
    fun save(message: Message): Message
    fun findById(id: UUID): Message?
    fun existsById(id: UUID): Boolean
    /**
     * One page of a conversation's readable history: not deleted, not a scheduled message that has
     * yet to go out, inside [window], and on the far side of [position] — at most [limit] rows, in
     * the direction of travel (see [HistoryPosition]).
     *
     * Keyset over `(serverTimestamp, id)`, so a page boundary that falls inside a run of equal
     * timestamps neither skips nor repeats a row (#750). [window] has no default on purpose: every
     * caller states whose history this is (#874).
     */
    fun findHistory(
        conversationId: UUID,
        window: HistoryWindow,
        position: HistoryPosition,
        limit: Int
    ): List<Message>

    /**
     * Messages across every conversation [userId] belongs to, stamped after [since] (default: seven
     * days ago), oldest first. Background sync's catch-up read.
     *
     * Cross-conversation, so the [HistoryWindow] policy cannot be passed in as one value — the
     * adapter applies `HistoryWindow.of` per row, in the query, against each membership's
     * `joined_at`. `HistoryVisibilityIntegrationTest` pins the two to the same answer.
     */
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
    /**
     * The newest message [userId] may read in each of [conversationIds] — the inbox preview.
     *
     * Per user since #874: the conversation's newest message is exactly what a member who joined
     * a group after it must not be shown, and before this the preview printed it in the inbox of
     * everyone just added. A conversation with nothing visible is absent from the map.
     */
    fun getLastMessages(conversationIds: List<UUID>, userId: UUID): Map<UUID, Message>
    fun getUnreadCounts(conversationIds: List<UUID>, userId: UUID): Map<UUID, Int>

    // Message management
    fun softDelete(messageId: UUID)
    fun updateContent(messageId: UUID, newContent: String, editedAt: Instant)

    // Media count
    fun countMediaInConversation(conversationId: UUID): Int

    // Batch delivery status lookup
    fun getDeliveryStatuses(messageIds: List<UUID>): List<MessageDeliveryStatus>

    // Media messages for shared media screen
    fun findMediaByConversationId(conversationId: UUID, window: HistoryWindow, limit: Int, offset: Int): List<Message>

    // Search — membership-scoped: results are restricted to conversations the requesting user belongs to.
    fun searchInConversation(
        conversationId: UUID,
        userId: UUID,
        window: HistoryWindow,
        query: String,
        limit: Int,
        offset: Int
    ): List<Message>

    /** Cross-conversation, so the [HistoryWindow] policy is applied per row in the adapter, as in [findUndeliveredForUser]. */
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

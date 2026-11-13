package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.StarredCursor
import com.muhabbet.messaging.domain.model.StarredMessage
import java.util.UUID

interface StarredMessageRepository {
    /**
     * Writes the bookmark if it is not there. Must be safe under concurrency: two simultaneous
     * calls for the same pair leave one row and neither fails.
     */
    fun star(userId: UUID, messageId: UUID)

    fun unstar(userId: UUID, messageId: UUID)

    /**
     * Up to [limit] of [userId]'s bookmarks starred strictly before [before] (or the newest, when
     * null), newest first — restricted to messages the user can still read: in a conversation they
     * are a member of now, and not deleted, view-once or still scheduled.
     *
     * The restriction is applied in the query, not by the caller, so a bookmark written before the
     * user left a conversation, or before a message changed state, can never surface its content.
     */
    fun findVisibleStarred(userId: UUID, before: StarredCursor?, limit: Int): List<StarredMessage>
}

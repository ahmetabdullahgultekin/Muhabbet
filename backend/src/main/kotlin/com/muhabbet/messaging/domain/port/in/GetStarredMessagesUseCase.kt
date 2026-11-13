package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.StarredMessagePage
import java.util.UUID

interface GetStarredMessagesUseCase {
    /**
     * [userId]'s bookmarks, most recently starred first, that they can still read: only messages in
     * conversations they are a member of now, and never deleted, view-once or not-yet-sent
     * scheduled messages.
     *
     * [cursor] is the `nextCursor` of the previous page, or null for the first page.
     */
    fun getStarred(userId: UUID, cursor: String?, limit: Int): StarredMessagePage
}

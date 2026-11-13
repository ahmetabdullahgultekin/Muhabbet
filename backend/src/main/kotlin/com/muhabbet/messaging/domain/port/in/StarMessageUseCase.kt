package com.muhabbet.messaging.domain.port.`in`

import java.util.UUID

interface StarMessageUseCase {
    /**
     * Bookmarks [messageId] for [userId]. Idempotent: starring a message that is already starred
     * succeeds and leaves exactly one bookmark.
     *
     * Refused with `MSG_NOT_FOUND` when the caller could not read the message through history —
     * including when it exists in a conversation they are not a member of.
     */
    fun star(userId: UUID, messageId: UUID)

    /** Removes [userId]'s own bookmark, if there is one. Touches nobody else's rows. */
    fun unstar(userId: UUID, messageId: UUID)
}

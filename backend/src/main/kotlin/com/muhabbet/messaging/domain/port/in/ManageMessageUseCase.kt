package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Message
import java.time.Instant
import java.util.UUID

interface ManageMessageUseCase {
    fun deleteMessage(messageId: UUID, requesterId: UUID)
    fun editMessage(messageId: UUID, requesterId: UUID, newContent: String): Message

    /**
     * Burns a view-once message for [userId] and releases its media in the same breath.
     *
     * Membership is authorized behind this port, so a non-member who guesses a messageId cannot
     * destroy someone else's message. Burning and releasing are one operation deliberately: no other
     * response in the API carries a view-once blob URL, so if this returned nothing the recipient
     * would have no way to see the photo at all, and if it released without burning the "once" would
     * be a suggestion.
     *
     * @throws com.muhabbet.shared.exception.BusinessException `MSG_VIEW_ONCE_ALREADY_VIEWED` if it
     *   has been opened, by this caller or anyone else.
     */
    fun markViewOnceViewed(messageId: UUID, userId: UUID): ViewOnceReveal
}

/** The media released by a single, successful burn. */
data class ViewOnceReveal(
    val messageId: UUID,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val viewedAt: Instant
)

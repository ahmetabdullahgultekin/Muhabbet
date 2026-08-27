package com.muhabbet.messaging.domain.port.`in`

import java.time.Instant
import java.util.UUID

/**
 * Opening a view-once message — its own in-port, split out of `ManageMessageUseCase` by #541.
 *
 * Delete and edit are things you do *to* a message you own. This is the opposite: a one-time,
 * destructive read by the recipient that also takes a blob out of object storage. It grew a
 * collaborator neither of the others needs, and leaving it where it was would have put a media
 * dependency on the class that handles every text message sent.
 */
interface BurnViewOnceUseCase {

    /**
     * Burns a view-once message for [userId] and releases its media in the same breath.
     *
     * Membership is authorized behind this port, so a non-member who guesses a messageId cannot
     * destroy someone else's message. Burning and releasing are one operation deliberately: no
     * other response in the API carries a view-once photo, so if this returned nothing the
     * recipient would have no way to see it at all, and if it released without burning the "once"
     * would be a suggestion.
     *
     * @throws com.muhabbet.shared.exception.BusinessException `MSG_VIEW_ONCE_ALREADY_VIEWED` if it
     *   has been opened, by this caller or anyone else.
     */
    fun markViewOnceViewed(messageId: UUID, userId: UUID): ViewOnceReveal
}

/**
 * The media released by a single, successful burn.
 *
 * [mediaBytes] is the release for anything sent since V24: the object was deleted before this was
 * built, so these bytes are the only copy that ever left the server and there is no URL for anyone
 * to keep (#541).
 *
 * [mediaUrl] is the fallback for a message whose blob cannot be destroyed — sent before V24, or
 * naming an object its sender did not upload. Exactly one of the two is set. Not a data class:
 * `ByteArray` compares by identity, so a generated `equals` would be misleading.
 */
class ViewOnceReveal(
    val messageId: UUID,
    val mediaBytes: ByteArray?,
    val mediaContentType: String?,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val viewedAt: Instant
)

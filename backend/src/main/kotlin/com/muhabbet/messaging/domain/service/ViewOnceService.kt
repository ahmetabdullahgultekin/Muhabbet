package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.BurnViewOnceUseCase
import com.muhabbet.messaging.domain.port.`in`.ViewOnceReveal
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MediaBytes
import com.muhabbet.messaging.domain.port.out.MediaObjectPort
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Opening a view-once message, and destroying what it showed.
 *
 * Split out of `MessageService` by #541. That class implements everything a message can do and had
 * grown to four use-case interfaces; this operation is the only one that reaches into object
 * storage, and the only destructive one, so it now owns its own collaborator instead of handing a
 * media port to the class that also processes every text message sent.
 */
open class ViewOnceService(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val mediaObjects: MediaObjectPort
) : BurnViewOnceUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Burns a view-once message and hands back its media, once.
     *
     * The order matters and every step earns its place:
     *
     * - **Authorize before anything else** — a non-member who knows the messageId must not be able
     *   to burn someone else's message (the IDOR closed by #55).
     * - **The sender is refused**, so opening your own view-once photo cannot consume the
     *   recipient's one look. The sender's own copy is stripped by `toSharedMessage` too; a
     *   view-once photo is not re-viewable by anyone once it leaves the composer.
     * - **The blob is destroyed before the message is marked spent**, and the bytes come back
     *   inline rather than as a URL — see [releaseMedia].
     * - **The write is the arbiter, not the read.** `viewedAt != null` above is a cheap rejection
     *   for the common case, but two taps can both pass it. The conditional UPDATE can only match
     *   once, so a zero row count means someone else won and this caller gets nothing.
     */
    @Transactional
    override fun markViewOnceViewed(messageId: UUID, userId: UUID): ViewOnceReveal {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        conversationRepository.findMember(message.conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        if (!message.viewOnce) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }

        if (message.viewedAt != null) {
            throw BusinessException(ErrorCode.MSG_VIEW_ONCE_ALREADY_VIEWED)
        }

        if (message.senderId == userId) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }

        val released = releaseMedia(message)

        val viewedAt = Instant.now()
        if (messageRepository.markViewOnceViewed(messageId, userId, viewedAt) == 0) {
            throw BusinessException(ErrorCode.MSG_VIEW_ONCE_ALREADY_VIEWED)
        }

        log.info("View-once message viewed: msg={}, user={}, purged={}", messageId, userId, released != null)
        return ViewOnceReveal(
            messageId = messageId,
            mediaBytes = released?.data,
            mediaContentType = released?.contentType,
            mediaUrl = if (released == null) message.mediaUrl else null,
            thumbnailUrl = if (released == null) message.thumbnailUrl else null,
            viewedAt = viewedAt
        )
    }

    /**
     * Takes the photo out of storage — bytes returned, object gone — or gives up and lets the
     * caller fall back to the stored URL.
     *
     * **Ownership is checked here rather than when the message was sent, because here is where the
     * consequence is.** `media_id` arrives on the send frame and is a client assertion; acting on
     * it destroys a file. A message naming a blob its own sender never uploaded is either a stale
     * client or an attempt to aim this at someone else's photo, and either way nothing is deleted.
     * Putting the check on the send path instead would have made every text message in the app
     * carry a media dependency it never uses, and would still have needed this one to be safe.
     *
     * **Read, delete, then mark** — those are two writes to two systems that can fail
     * independently, so the order decides which way a failure falls. This way a crash between them
     * leaves the photo unreachable and the message still showing as available: the recipient loses
     * their one look, which is a bad afternoon. The other way round leaves it marked burned and
     * still fetchable, which is #541 — and that lasted seven days, because `media_url` is a
     * *presigned* URL and the signature was minted with a seven-day expiry.
     *
     * Nothing here mints a fresh URL either, however short-lived. Any URL is a credential that
     * outlives the response it travelled in, and "once" cannot be expressed as a duration.
     *
     * A message with no reference at all — anything sent before V24 — has no object to find and
     * none to destroy, so it keeps the old, weaker promise. That is stated plainly on
     * `ViewOnceRevealResponse` rather than papered over.
     */
    private fun releaseMedia(message: Message): MediaBytes? {
        val mediaId = message.mediaId ?: return null
        if (mediaObjects.findUploaderId(mediaId) != message.senderId) {
            log.warn(
                "View-once media not destroyed, the message names a blob its sender did not upload: msg={}, media={}",
                message.id,
                mediaId
            )
            return null
        }
        return mediaObjects.takeAndDestroy(mediaId)
    }
}

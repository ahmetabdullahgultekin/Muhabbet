package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * What a URL a client asked us to hang on a message or a status actually *is*.
 *
 * The adapter reports provenance; the domain decides what may be published. Keeping those apart is
 * what lets the forwarding exception live in [com.muhabbet.messaging.domain.service.MessageService]
 * — where the messages are — instead of leaking into an adapter that would then need to know about
 * conversation membership.
 */
sealed interface MediaAttachment {

    /** A blob in our own store, uploaded by [uploaderId]. */
    data class OwnStorage(val uploaderId: UUID) : MediaAttachment

    /**
     * The public GIF/sticker host the picker draws from. Nobody uploads these and nobody owns them,
     * so there is no ownership to check — what makes them safe is that the host is fixed and is not
     * the sender's to choose.
     */
    data object PublicStickerHost : MediaAttachment

    /** An address of the sender's own choosing. Never publishable. */
    data object Unrecognised : MediaAttachment

    /**
     * Whether [userId] may publish this attachment on their own account, with no other claim.
     * Shared by the message path and the status path so the two cannot drift; the forwarding
     * exception is applied by the caller on top of this, never instead of it.
     */
    fun isPublishableBy(userId: UUID): Boolean = when (this) {
        is OwnStorage -> uploaderId == userId
        PublicStickerHost -> true
        Unrecognised -> false
    }
}

/**
 * Where a media URL came from.
 *
 * Messaging declares only the question — never `media_files`, never the media repository — so the
 * adapter behind this port stays the one place messaging and media meet. Same shape as
 * [BlockPolicyPort] and [ReadReceiptPolicyPort], and for the same reason.
 */
interface MediaAttachmentPolicyPort {

    /** Classifies [url]. Never throws: an unparseable string is simply [MediaAttachment.Unrecognised]. */
    fun classify(url: String): MediaAttachment
}

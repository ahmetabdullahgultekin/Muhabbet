package com.muhabbet.media.domain.port.`in`

import java.util.UUID

/**
 * What another module may ask of a stored media object beyond rendering it: who put it there, and
 * "give me the bytes and destroy it".
 *
 * Separate from [GetMediaUrlUseCase] because it answers a different kind of question. That one
 * mints a URL for something that goes on existing; this one is the seam for media that must stop
 * existing — view-once today (#541) — and for the ownership check that has to precede binding a
 * blob to a message.
 *
 * Kept off `MediaService`, which already implements the three it is allowed
 * ([UploadMediaUseCase], [GetMediaUrlUseCase], [GetStorageUsageUseCase]); a fourth would also put a
 * destructive operation inside the class whose other job is to accept uploads.
 */
interface ManageMediaObjectUseCase {

    /**
     * Who uploaded [mediaId], or null if there is no such object.
     *
     * The question a caller must ask before recording a media reference against a message: without
     * it a client could name someone else's blob in its own message and later burn it, which is a
     * delete primitive pointed at another user's file. The answer comes from the `media_files` row
     * the server wrote, never from a string the client supplied (#267).
     */
    fun findUploaderId(mediaId: UUID): UUID?

    /**
     * Reads [mediaId]'s bytes, then deletes the object, its thumbnail and its row — in that order.
     *
     * The order is the contract, not an implementation detail. Reading first is what lets the
     * caller hand the bytes to the one viewer entitled to them while the blob is already gone; if
     * anything after the delete fails, the failure direction is "unreachable but not yet marked
     * spent", which costs a user their photo and leaks nothing. The reverse — marked spent, still
     * fetchable — is the defect this exists to close.
     *
     * Idempotent: a second call finds no row, returns null and deletes nothing.
     */
    fun takeAndDestroy(mediaId: UUID): TakenMedia?
}

/**
 * Bytes handed over by [ManageMediaObjectUseCase.takeAndDestroy], after the object holding them was
 * deleted. Not a data class: a `ByteArray` compares by identity, so a generated `equals` would be
 * quietly wrong for anyone who tried to use it.
 */
class TakenMedia(val bytes: ByteArray, val contentType: String)

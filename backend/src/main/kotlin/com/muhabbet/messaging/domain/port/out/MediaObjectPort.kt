package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * What messaging needs to know and do about a stored blob.
 *
 * Media is owned by the media module. Messaging declares only the two questions it has — "is this
 * the sender's own upload" and "hand me the bytes and destroy it" — and never names a bucket, a
 * key or a `MediaFile`. Same shape as [BlockPolicyPort] and [UserDirectoryPort], and for the same
 * reason: the adapter behind this port stays the one place the two modules meet.
 *
 * Both methods exist because of #541. Ownership is checked when a media reference is *recorded*, so
 * that destruction later can be trusted: a message may only point at a blob its own sender
 * uploaded, or the burn becomes a way to delete other people's files.
 */
interface MediaObjectPort {

    /** Who uploaded [mediaId], or null if no such object exists. */
    fun findUploaderId(mediaId: UUID): UUID?

    /**
     * Returns [mediaId]'s bytes and destroys the object behind them, in that order — see
     * `ManageMediaObjectUseCase.takeAndDestroy`. Null when there is nothing left to take, which a
     * retry of a partially failed burn will see.
     */
    fun takeAndDestroy(mediaId: UUID): MediaBytes?
}

/**
 * Bytes released by [MediaObjectPort.takeAndDestroy], after their object was deleted. Not a data
 * class — `ByteArray` compares by identity, so a generated `equals` would mislead.
 */
class MediaBytes(val data: ByteArray, val contentType: String)

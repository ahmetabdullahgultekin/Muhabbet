package com.muhabbet.media.domain.service

import com.muhabbet.media.domain.port.`in`.ManageMediaObjectUseCase
import com.muhabbet.media.domain.port.`in`.TakenMedia
import com.muhabbet.media.domain.port.out.MediaFileRepository
import com.muhabbet.media.domain.port.out.MediaStoragePort
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * The destructive half of the media module (#541), deliberately not part of [MediaService].
 *
 * `MediaService` accepts uploads and mints URLs; this one takes bytes away. Keeping them apart is
 * partly the "three use cases per service" rule and mostly that a class which both stores and
 * destroys invites a future edit that does the second while meaning the first.
 */
class MediaObjectService(
    private val mediaFileRepository: MediaFileRepository,
    private val mediaStoragePort: MediaStoragePort
) : ManageMediaObjectUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun findUploaderId(mediaId: UUID): UUID? = mediaFileRepository.findById(mediaId)?.uploaderId

    /**
     * Read, delete, forget — and the order is the whole guarantee.
     *
     * The bytes come out first because the caller has to be able to show them; by the time this
     * returns, the object they came from no longer exists, so the copy in the response is the only
     * one that was ever released. Nothing here mints a URL, which is the point: a presigned URL is a
     * credential with a lifetime, and a lifetime is exactly what "view once" cannot have.
     *
     * The row is deleted last. If the process dies between the object delete and the row delete,
     * the metadata outlives the bytes — a row pointing at nothing, which is harmless and which a
     * retry cleans up. Deleting the row first would strand the object with no key anywhere,
     * reachable by anyone still holding a presigned URL for it and impossible to find again.
     *
     * The thumbnail is deleted too. It is a smaller copy of the same photo, and a seal that leaves
     * a legible 320px version behind is not a seal.
     */
    override fun takeAndDestroy(mediaId: UUID): TakenMedia? {
        val mediaFile = mediaFileRepository.findById(mediaId) ?: return null

        val bytes = mediaStoragePort.getObject(mediaFile.fileKey)

        mediaStoragePort.deleteObject(mediaFile.fileKey)
        mediaFile.thumbnailKey?.let { mediaStoragePort.deleteObject(it) }
        mediaFileRepository.deleteById(mediaId)

        log.info("Media object destroyed after release: id={}, key={}", mediaId, mediaFile.fileKey)
        return bytes?.let { TakenMedia(bytes = it, contentType = mediaFile.contentType) }
    }
}

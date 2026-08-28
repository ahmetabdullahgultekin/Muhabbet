package com.muhabbet.media.domain.port.out

import com.muhabbet.media.domain.model.MediaFile
import java.util.UUID

interface MediaFileRepository {
    fun save(mediaFile: MediaFile): MediaFile
    fun findById(id: UUID): MediaFile?
    fun sumSizeByUploaderAndContentTypePrefix(uploaderId: UUID, prefix: String): Long
    fun countByUploaderAndContentTypePrefix(uploaderId: UUID, prefix: String): Long

    /**
     * Forgets a media object's metadata once its bytes are gone (#541). Idempotent — deleting a row
     * that is not there is not an error, because the burn path may be retried.
     */
    fun deleteById(id: UUID)
}

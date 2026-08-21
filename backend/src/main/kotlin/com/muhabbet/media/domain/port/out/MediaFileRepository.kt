package com.muhabbet.media.domain.port.out

import com.muhabbet.media.domain.model.MediaFile
import java.util.UUID

interface MediaFileRepository {
    fun save(mediaFile: MediaFile): MediaFile
    fun findById(id: UUID): MediaFile?

    /**
     * The file whose original or thumbnail is stored under [key], or null.
     *
     * Both keys are searched because a message carries both URLs and a thumbnail is not a row of
     * its own — `thumbnails/…` lives in the `thumbnail_key` column of the file it belongs to, so
     * looking only at `file_key` would treat every legitimate thumbnail as an unknown address.
     */
    fun findByObjectKey(key: String): MediaFile?
    fun sumSizeByUploaderAndContentTypePrefix(uploaderId: UUID, prefix: String): Long
    fun countByUploaderAndContentTypePrefix(uploaderId: UUID, prefix: String): Long
}

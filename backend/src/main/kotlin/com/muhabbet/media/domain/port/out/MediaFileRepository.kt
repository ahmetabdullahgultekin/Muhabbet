package com.muhabbet.media.domain.port.out

import com.muhabbet.media.domain.model.MediaFile
import java.util.UUID

interface MediaFileRepository {
    fun save(mediaFile: MediaFile): MediaFile
    fun findById(id: UUID): MediaFile?
}

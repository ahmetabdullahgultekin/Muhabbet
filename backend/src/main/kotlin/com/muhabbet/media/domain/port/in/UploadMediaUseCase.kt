package com.muhabbet.media.domain.port.`in`

import com.muhabbet.media.domain.model.MediaFile
import java.io.InputStream
import java.util.UUID

interface UploadMediaUseCase {
    fun uploadImage(command: UploadImageCommand): MediaFile
}

data class UploadImageCommand(
    val uploaderId: UUID,
    val inputStream: InputStream,
    val contentType: String,
    val sizeBytes: Long,
    val originalFilename: String?
)

package com.muhabbet.media.domain.port.`in`

import com.muhabbet.media.domain.model.MediaFile
import java.io.InputStream
import java.util.UUID

interface UploadMediaUseCase {
    fun uploadImage(command: UploadImageCommand): MediaFile
    fun uploadAudio(command: UploadAudioCommand): MediaFile
    fun uploadDocument(command: UploadDocumentCommand): MediaFile
}

data class UploadImageCommand(
    val uploaderId: UUID,
    val inputStream: InputStream,
    val contentType: String,
    val sizeBytes: Long,
    val originalFilename: String?
)

data class UploadAudioCommand(
    val uploaderId: UUID,
    val inputStream: InputStream,
    val contentType: String,
    val sizeBytes: Long,
    val durationSeconds: Int?,
    val originalFilename: String?
)

data class UploadDocumentCommand(
    val uploaderId: UUID,
    val inputStream: InputStream,
    val contentType: String,
    val sizeBytes: Long,
    val originalFilename: String?
)

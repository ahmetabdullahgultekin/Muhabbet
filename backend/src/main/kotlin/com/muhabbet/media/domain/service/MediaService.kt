package com.muhabbet.media.domain.service

import com.muhabbet.media.domain.model.MediaFile
import com.muhabbet.media.domain.port.`in`.GetMediaUrlUseCase
import com.muhabbet.media.domain.port.`in`.GetStorageUsageUseCase
import com.muhabbet.media.domain.port.`in`.MediaUrlResult
import com.muhabbet.media.domain.port.`in`.UploadAudioCommand
import com.muhabbet.media.domain.port.`in`.UploadDocumentCommand
import com.muhabbet.media.domain.port.`in`.UploadImageCommand
import com.muhabbet.media.domain.port.`in`.UploadMediaUseCase
import com.muhabbet.shared.dto.StorageUsageResponse
import com.muhabbet.media.domain.port.out.MediaFileRepository
import com.muhabbet.media.domain.port.out.MediaStoragePort
import com.muhabbet.media.domain.port.out.ThumbnailPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.util.UUID

open class MediaService(
    private val mediaStoragePort: MediaStoragePort,
    private val mediaFileRepository: MediaFileRepository,
    private val thumbnailPort: ThumbnailPort,
    private val thumbnailWidth: Int,
    private val thumbnailHeight: Int
) : UploadMediaUseCase, GetMediaUrlUseCase, GetStorageUsageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun uploadImage(command: UploadImageCommand): MediaFile {
        // Validate content type
        if (command.contentType !in ValidationRules.ALLOWED_IMAGE_TYPES) {
            throw BusinessException(ErrorCode.MEDIA_UNSUPPORTED_TYPE)
        }

        // Validate size
        if (command.sizeBytes > ValidationRules.MAX_IMAGE_SIZE_BYTES) {
            throw BusinessException(ErrorCode.MEDIA_TOO_LARGE)
        }

        val imageBytes = command.inputStream.readBytes()
        val fileId = UUID.randomUUID()
        val extension = extensionFromMime(command.contentType)
        val fileKey = "images/${command.uploaderId}/$fileId.$extension"
        val thumbnailKey = "thumbnails/${command.uploaderId}/$fileId.jpg"

        try {
            // Upload original
            mediaStoragePort.putObject(
                key = fileKey,
                inputStream = ByteArrayInputStream(imageBytes),
                contentType = command.contentType,
                sizeBytes = command.sizeBytes
            )

            // Generate and upload thumbnail
            val thumbnail = thumbnailPort.generateThumbnail(
                inputStream = ByteArrayInputStream(imageBytes),
                contentType = command.contentType,
                maxWidth = thumbnailWidth,
                maxHeight = thumbnailHeight
            )
            mediaStoragePort.putObject(
                key = thumbnailKey,
                inputStream = ByteArrayInputStream(thumbnail.data),
                contentType = thumbnail.contentType,
                sizeBytes = thumbnail.data.size.toLong()
            )

            // Save metadata
            val mediaFile = mediaFileRepository.save(
                MediaFile(
                    id = fileId,
                    uploaderId = command.uploaderId,
                    fileKey = fileKey,
                    contentType = command.contentType,
                    sizeBytes = command.sizeBytes,
                    thumbnailKey = thumbnailKey,
                    originalFilename = command.originalFilename
                )
            )

            log.info("Image uploaded: id={}, key={}, size={}", mediaFile.id, fileKey, command.sizeBytes)
            return mediaFile
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("Image upload failed: {}", e.message, e)
            throw BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED, cause = e)
        }
    }

    override fun uploadAudio(command: UploadAudioCommand): MediaFile {
        if (command.contentType !in ValidationRules.ALLOWED_VOICE_TYPES) {
            throw BusinessException(ErrorCode.MEDIA_UNSUPPORTED_TYPE)
        }
        if (command.sizeBytes > ValidationRules.MAX_VOICE_SIZE_BYTES) {
            throw BusinessException(ErrorCode.MEDIA_TOO_LARGE)
        }

        val audioBytes = command.inputStream.readBytes()
        val fileId = UUID.randomUUID()
        val extension = audioExtensionFromMime(command.contentType)
        val fileKey = "audio/${command.uploaderId}/$fileId.$extension"

        try {
            mediaStoragePort.putObject(
                key = fileKey,
                inputStream = ByteArrayInputStream(audioBytes),
                contentType = command.contentType,
                sizeBytes = command.sizeBytes
            )

            val mediaFile = mediaFileRepository.save(
                MediaFile(
                    id = fileId,
                    uploaderId = command.uploaderId,
                    fileKey = fileKey,
                    contentType = command.contentType,
                    sizeBytes = command.sizeBytes,
                    durationSeconds = command.durationSeconds,
                    originalFilename = command.originalFilename
                )
            )

            log.info("Audio uploaded: id={}, key={}, size={}, duration={}s", mediaFile.id, fileKey, command.sizeBytes, command.durationSeconds)
            return mediaFile
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("Audio upload failed: {}", e.message, e)
            throw BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED, cause = e)
        }
    }

    override fun uploadDocument(command: UploadDocumentCommand): MediaFile {
        // Checked before the size, and before a single byte is read: the type is the cheaper test
        // and the one that matters. Until #287 this method checked size and nothing else, while
        // every other upload kind had an allowlist — so `text/html` was accepted, stored, and then
        // served over a presigned URL from the media host with that content type, which is stored
        // XSS against anyone who opens the link.
        if (!ValidationRules.isAllowedDocumentType(command.contentType)) {
            log.warn(
                "Document upload refused, unsupported content type: uploader={}, type={}",
                command.uploaderId,
                command.contentType
            )
            throw BusinessException(ErrorCode.MEDIA_UNSUPPORTED_TYPE)
        }
        if (command.sizeBytes > ValidationRules.MAX_DOCUMENT_SIZE_BYTES) {
            throw BusinessException(ErrorCode.MEDIA_TOO_LARGE)
        }

        val fileBytes = command.inputStream.readBytes()
        val fileId = UUID.randomUUID()
        val extension = command.originalFilename?.substringAfterLast('.', "bin") ?: "bin"
        val fileKey = "documents/${command.uploaderId}/$fileId.$extension"

        try {
            mediaStoragePort.putObject(
                key = fileKey,
                inputStream = ByteArrayInputStream(fileBytes),
                contentType = command.contentType,
                sizeBytes = command.sizeBytes
            )

            val mediaFile = mediaFileRepository.save(
                MediaFile(
                    id = fileId,
                    uploaderId = command.uploaderId,
                    fileKey = fileKey,
                    contentType = command.contentType,
                    sizeBytes = command.sizeBytes,
                    originalFilename = command.originalFilename
                )
            )

            log.info("Document uploaded: id={}, key={}, size={}", mediaFile.id, fileKey, command.sizeBytes)
            return mediaFile
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("Document upload failed: {}", e.message, e)
            throw BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED, cause = e)
        }
    }

    override fun getPresignedUrl(mediaId: UUID, requestingUserId: UUID): MediaUrlResult {
        val mediaFile = mediaFileRepository.findById(mediaId)
            ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)

        // Only the uploader can mint a URL for their own blob.
        //
        // This used to also accept "a conversation you belong to has a message referencing this file".
        // That decided authorization from `messages.media_url`, a column written straight from the
        // request body — so a user could send themselves a message naming someone else's file and read
        // it (#267). Since the reference was client-controlled, no amount of tightening the comparison
        // made the question safe to ask.
        //
        // Dropping the branch costs nothing today: no client calls this endpoint. Media is rendered
        // from the presigned URL already stored on the message, not by minting a new one. If
        // member-based access is ever needed — a URL-refresh feature would need it, since presigned
        // URLs expire — it has to key off a server-resolved media id rather than a client-supplied
        // string.
        if (mediaFile.uploaderId != requestingUserId) {
            throw BusinessException(ErrorCode.MEDIA_FORBIDDEN)
        }

        val url = mediaStoragePort.getPresignedUrl(mediaFile.fileKey)
        val thumbnailUrl = mediaFile.thumbnailKey?.let { mediaStoragePort.getPresignedUrl(it) }

        return MediaUrlResult(url = url, thumbnailUrl = thumbnailUrl)
    }

    override fun getStorageUsage(userId: UUID): StorageUsageResponse {
        val imageBytes = mediaFileRepository.sumSizeByUploaderAndContentTypePrefix(userId, "image/")
        val audioBytes = mediaFileRepository.sumSizeByUploaderAndContentTypePrefix(userId, "audio/")
        val documentBytes = mediaFileRepository.sumSizeByUploaderAndContentTypePrefix(userId, "application/")
        val imageCount = mediaFileRepository.countByUploaderAndContentTypePrefix(userId, "image/")
        val audioCount = mediaFileRepository.countByUploaderAndContentTypePrefix(userId, "audio/")
        val documentCount = mediaFileRepository.countByUploaderAndContentTypePrefix(userId, "application/")

        return StorageUsageResponse(
            totalBytes = imageBytes + audioBytes + documentBytes,
            imageBytes = imageBytes,
            audioBytes = audioBytes,
            documentBytes = documentBytes,
            imageCount = imageCount.toInt(),
            audioCount = audioCount.toInt(),
            documentCount = documentCount.toInt()
        )
    }

    private fun extensionFromMime(mime: String): String = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "bin"
    }

    private fun audioExtensionFromMime(mime: String): String = when (mime) {
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/mp4" -> "m4a"
        else -> "bin"
    }
}

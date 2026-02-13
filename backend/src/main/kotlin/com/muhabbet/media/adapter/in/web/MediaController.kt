package com.muhabbet.media.adapter.`in`.web

import com.muhabbet.media.domain.port.`in`.GetMediaUrlUseCase
import com.muhabbet.media.domain.port.`in`.UploadAudioCommand
import com.muhabbet.media.domain.port.`in`.UploadDocumentCommand
import com.muhabbet.media.domain.port.`in`.UploadImageCommand
import com.muhabbet.media.domain.port.`in`.UploadMediaUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.MediaUploadResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/media")
class MediaController(
    private val uploadMediaUseCase: UploadMediaUseCase,
    private val getMediaUrlUseCase: GetMediaUrlUseCase
) {

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadImage(
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<ApiResponse<MediaUploadResponse>> {
        val userId = AuthenticatedUser.currentUserId()

        val mediaFile = uploadMediaUseCase.uploadImage(
            UploadImageCommand(
                uploaderId = userId,
                inputStream = file.inputStream,
                contentType = file.contentType ?: "application/octet-stream",
                sizeBytes = file.size,
                originalFilename = file.originalFilename
            )
        )

        val urls = getMediaUrlUseCase.getPresignedUrl(mediaFile.id)

        val response = MediaUploadResponse(
            mediaId = mediaFile.id.toString(),
            url = urls.url,
            thumbnailUrl = urls.thumbnailUrl,
            contentType = mediaFile.contentType,
            sizeBytes = mediaFile.sizeBytes
        )

        return ApiResponseBuilder.created(response)
    }

    @PostMapping("/audio", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadAudio(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) durationSeconds: Int?
    ): ResponseEntity<ApiResponse<MediaUploadResponse>> {
        val userId = AuthenticatedUser.currentUserId()

        val mediaFile = uploadMediaUseCase.uploadAudio(
            UploadAudioCommand(
                uploaderId = userId,
                inputStream = file.inputStream,
                contentType = file.contentType ?: "audio/ogg",
                sizeBytes = file.size,
                durationSeconds = durationSeconds,
                originalFilename = file.originalFilename
            )
        )

        val urls = getMediaUrlUseCase.getPresignedUrl(mediaFile.id)

        val response = MediaUploadResponse(
            mediaId = mediaFile.id.toString(),
            url = urls.url,
            thumbnailUrl = null,
            contentType = mediaFile.contentType,
            sizeBytes = mediaFile.sizeBytes,
            durationSeconds = mediaFile.durationSeconds
        )

        return ApiResponseBuilder.created(response)
    }

    @PostMapping("/document", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadDocument(
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<ApiResponse<MediaUploadResponse>> {
        val userId = AuthenticatedUser.currentUserId()

        val mediaFile = uploadMediaUseCase.uploadDocument(
            UploadDocumentCommand(
                uploaderId = userId,
                inputStream = file.inputStream,
                contentType = file.contentType ?: "application/octet-stream",
                sizeBytes = file.size,
                originalFilename = file.originalFilename
            )
        )

        val urls = getMediaUrlUseCase.getPresignedUrl(mediaFile.id)

        val response = MediaUploadResponse(
            mediaId = mediaFile.id.toString(),
            url = urls.url,
            thumbnailUrl = null,
            contentType = mediaFile.contentType,
            sizeBytes = mediaFile.sizeBytes
        )

        return ApiResponseBuilder.created(response)
    }

    @GetMapping("/{mediaId}/url")
    fun getPresignedUrl(
        @PathVariable mediaId: UUID
    ): ResponseEntity<ApiResponse<MediaUploadResponse>> {
        val mediaFile = getMediaUrlUseCase.getPresignedUrl(mediaId)

        val response = MediaUploadResponse(
            mediaId = mediaId.toString(),
            url = mediaFile.url,
            thumbnailUrl = mediaFile.thumbnailUrl,
            contentType = "",
            sizeBytes = 0
        )

        return ApiResponseBuilder.ok(response)
    }
}

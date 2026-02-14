package com.muhabbet.app.data.repository

import com.muhabbet.app.platform.compressImage
import com.muhabbet.shared.dto.MediaUploadResponse

/**
 * Centralized media upload helper that ensures compression is always applied.
 * Callers should use this instead of calling MediaRepository directly for media uploads.
 */
class MediaUploadHelper(private val mediaRepository: MediaRepository) {

    /**
     * Upload an image with automatic compression.
     * Always compresses to JPEG (max 1280px, quality 80) before upload.
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        fileName: String,
        maxDimension: Int = 1280,
        quality: Int = 80
    ): MediaUploadResponse {
        val compressed = compressImage(bytes, maxDimension, quality)
        return mediaRepository.uploadImage(
            bytes = compressed,
            mimeType = "image/jpeg",
            fileName = ensureJpegExtension(fileName)
        )
    }

    /**
     * Upload a profile photo with more aggressive compression.
     * Uses smaller max dimension (512px) for profile photos.
     */
    suspend fun uploadProfilePhoto(
        bytes: ByteArray,
        fileName: String
    ): MediaUploadResponse {
        val compressed = compressImage(bytes, maxDimension = 512, quality = 75)
        return mediaRepository.uploadImage(
            bytes = compressed,
            mimeType = "image/jpeg",
            fileName = ensureJpegExtension(fileName)
        )
    }

    /**
     * Upload audio — no compression needed (already encoded as OGG/OPUS 32kbps).
     */
    suspend fun uploadAudio(
        bytes: ByteArray,
        fileName: String,
        mimeType: String = "audio/ogg",
        durationSeconds: Int? = null
    ): MediaUploadResponse {
        return mediaRepository.uploadAudio(
            bytes = bytes,
            mimeType = mimeType,
            fileName = fileName,
            durationSeconds = durationSeconds
        )
    }

    /**
     * Upload a document — no compression (PDFs, DOCs etc. should not be altered).
     */
    suspend fun uploadDocument(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): MediaUploadResponse {
        return mediaRepository.uploadDocument(
            bytes = bytes,
            mimeType = mimeType,
            fileName = fileName
        )
    }

    /**
     * Upload a thumbnail image for video messages.
     * Compresses aggressively since thumbnails are small previews.
     */
    suspend fun uploadThumbnail(
        bytes: ByteArray,
        fileName: String
    ): MediaUploadResponse {
        val compressed = compressImage(bytes, maxDimension = 320, quality = 60)
        return mediaRepository.uploadImage(
            bytes = compressed,
            mimeType = "image/jpeg",
            fileName = "thumb_${ensureJpegExtension(fileName)}"
        )
    }

    private fun ensureJpegExtension(fileName: String): String {
        val name = fileName.substringBeforeLast(".")
        return "$name.jpg"
    }
}

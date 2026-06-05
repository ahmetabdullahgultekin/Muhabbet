package com.muhabbet.app.data.repository

import com.muhabbet.app.crypto.MediaEncryptor
import com.muhabbet.app.platform.compressImage
import com.muhabbet.shared.dto.MediaUploadResponse
import com.muhabbet.shared.port.MediaKeyMaterial

/**
 * Centralized media upload helper that ensures compression is always applied.
 * Callers should use this instead of calling MediaRepository directly for media uploads.
 *
 * E2E media (Tier 1.4): a [MediaEncryptor] sits between compression and upload. When the
 * media-E2E flag is OFF (default) it is a pass-through and the bytes uploaded are byte-identical to
 * today's plaintext path. When ON, the compressed bytes are AES-256-GCM-encrypted before upload and
 * the per-media [MediaKeyMaterial] is returned so the caller can ship it inside the E2E-encrypted
 * message body (the server/MinIO only ever stores ciphertext). The flag is OFF in production until
 * sign-off + crypto review — see `docs/e2e-rollout-runbook.md`.
 */
class MediaUploadHelper(
    private val mediaRepository: MediaRepository,
    private val mediaEncryptor: MediaEncryptor = MediaEncryptor()
) {

    /**
     * An upload result that also carries the per-media key material when the blob was encrypted.
     * [keyMaterial] is null for the plaintext path (flag OFF or graceful fallback); when non-null
     * the caller MUST place it inside the (E2E-encrypted) message body so the recipient can decrypt.
     */
    data class UploadResult(
        val response: MediaUploadResponse,
        val keyMaterial: MediaKeyMaterial?
    )

    /**
     * Upload an image with automatic compression.
     * Always compresses to JPEG (max 1280px, quality 80) before upload.
     *
     * Plaintext path (flag OFF): byte-identical to the legacy behavior. Returns the upload response
     * directly; any media encryption is invisible to callers that use this overload.
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
     * Upload an image, returning the per-media [MediaKeyMaterial] when E2E media is active so the
     * caller can embed it in the encrypted message body. When the flag is OFF the [compressImage]
     * output is uploaded verbatim (plaintext) and [UploadResult.keyMaterial] is null — identical to
     * [uploadImage]. This is the call-site seam for the canary rollout (flag still OFF in prod).
     */
    suspend fun uploadImageE2E(
        bytes: ByteArray,
        fileName: String,
        maxDimension: Int = 1280,
        quality: Int = 80
    ): UploadResult {
        val compressed = compressImage(bytes, maxDimension, quality)
        val encrypted = mediaEncryptor.encryptForUpload(compressed)
        val response = mediaRepository.uploadImage(
            bytes = encrypted.blob,
            mimeType = "image/jpeg",
            fileName = ensureJpegExtension(fileName)
        )
        return UploadResult(response, encrypted.keyMaterial)
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

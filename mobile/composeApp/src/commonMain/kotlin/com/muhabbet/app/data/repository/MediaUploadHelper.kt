package com.muhabbet.app.data.repository

import com.muhabbet.app.crypto.MediaEncryptor
import com.muhabbet.app.data.local.MediaQuality
import com.muhabbet.app.data.local.TokenStorage
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
    private val tokenStorage: TokenStorage,
    private val mediaEncryptor: MediaEncryptor = MediaEncryptor()
) {

    /**
     * Read per upload rather than cached, so switching the setting takes effect on the next photo
     * instead of the next app launch.
     */
    private fun chatImageQuality(): MediaQuality =
        MediaQuality.fromStorageKey(tokenStorage.getMediaQuality())

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
     * Upload an image with automatic compression, at the [MediaQuality] the user selected in
     * Settings (standard by default).
     *
     * The dimension and quality are no longer parameters: no caller ever passed them, and leaving
     * them overridable would have re-created the bug this fixes by letting an upload site quietly
     * opt out of the user's choice. One profile, chosen in one place.
     *
     * Plaintext path (flag OFF): any media encryption is invisible to callers of this overload.
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        fileName: String
    ): MediaUploadResponse {
        val profile = chatImageQuality()
        val compressed = compressImage(bytes, profile.maxDimension, profile.jpegQuality)
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
        fileName: String
    ): UploadResult {
        val profile = chatImageQuality()
        val compressed = compressImage(bytes, profile.maxDimension, profile.jpegQuality)
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
     *
     * Deliberately ignores the user's [MediaQuality] choice: an avatar is displayed at a few dozen
     * dp, so HD would buy bytes and no visible difference.
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
     *
     * Ignores [MediaQuality] for the same reason as the profile photo: it is a preview, not the
     * media itself.
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

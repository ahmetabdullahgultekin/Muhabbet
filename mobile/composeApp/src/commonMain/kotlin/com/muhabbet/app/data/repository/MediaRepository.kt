package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.UploadPart
import com.muhabbet.app.data.remote.requireData
import com.muhabbet.shared.dto.MediaUploadResponse
import com.muhabbet.shared.dto.StorageUsageResponse

/**
 * Media endpoints, all through [ApiClient]'s checked path (#798).
 *
 * There used to be three copy-pasted multipart posts on the raw `httpClient` here, none of which
 * looked at the status, plus an unchecked `downloadMedia` that nothing called and that would have
 * handed a 403 page to the decryptor as if it were the file. Every call now either returns what the
 * server accepted or throws a typed [com.muhabbet.app.data.remote.DataException].
 *
 * [onProgress] receives bytes sent and the total; it is optional so callers that show no progress
 * need not pass it.
 */
class MediaRepository(
    private val apiClient: ApiClient,
) {

    suspend fun uploadImage(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        onProgress: ((sent: Long, total: Long?) -> Unit)? = null,
    ): MediaUploadResponse = upload(IMAGE_PATH, UploadPart(bytes, mimeType, fileName), onProgress = onProgress)

    suspend fun uploadAudio(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        durationSeconds: Int?,
        onProgress: ((sent: Long, total: Long?) -> Unit)? = null,
    ): MediaUploadResponse = upload(
        AUDIO_PATH,
        UploadPart(bytes, mimeType, fileName),
        fields = durationSeconds?.let { mapOf(DURATION_FIELD to it.toString()) } ?: emptyMap(),
        onProgress = onProgress,
    )

    suspend fun uploadDocument(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        onProgress: ((sent: Long, total: Long?) -> Unit)? = null,
    ): MediaUploadResponse = upload(DOCUMENT_PATH, UploadPart(bytes, mimeType, fileName), onProgress = onProgress)

    suspend fun getStorageUsage(): StorageUsageResponse =
        apiClient.get<StorageUsageResponse>(STORAGE_PATH).requireData()

    private suspend fun upload(
        path: String,
        part: UploadPart,
        fields: Map<String, String> = emptyMap(),
        onProgress: ((sent: Long, total: Long?) -> Unit)?,
    ): MediaUploadResponse = apiClient.upload<MediaUploadResponse>(path, part, fields, onProgress).requireData()

    private companion object {
        const val IMAGE_PATH = "/api/v1/media/upload"
        const val AUDIO_PATH = "/api/v1/media/audio"
        const val DOCUMENT_PATH = "/api/v1/media/document"
        const val STORAGE_PATH = "/api/v1/media/storage"
        const val DURATION_FIELD = "durationSeconds"
    }
}

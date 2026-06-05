package com.muhabbet.app.data.repository

import com.muhabbet.app.crypto.MediaEncryptor
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.MediaUploadResponse
import com.muhabbet.shared.dto.StorageUsageResponse
import com.muhabbet.shared.port.MediaKeyMaterial
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.serializer

class MediaRepository(
    private val apiClient: ApiClient,
    private val mediaEncryptor: MediaEncryptor = MediaEncryptor()
) {

    suspend fun uploadImage(bytes: ByteArray, mimeType: String, fileName: String): MediaUploadResponse {
        val response = apiClient.httpClient.post("${ApiClient.BASE_URL}/api/v1/media/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        })
                    }
                )
            )
        }
        val text = response.bodyAsText()
        val apiResponse = apiClient.json.decodeFromString(
            ApiResponse.serializer(serializer<MediaUploadResponse>()),
            text
        )
        return apiResponse.data ?: throw Exception(apiResponse.error?.message ?: "Upload failed")
    }

    suspend fun uploadAudio(bytes: ByteArray, mimeType: String, fileName: String, durationSeconds: Int?): MediaUploadResponse {
        val response = apiClient.httpClient.post("${ApiClient.BASE_URL}/api/v1/media/audio") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        })
                        if (durationSeconds != null) {
                            append("durationSeconds", durationSeconds.toString())
                        }
                    }
                )
            )
        }
        val text = response.bodyAsText()
        val apiResponse = apiClient.json.decodeFromString(
            ApiResponse.serializer(serializer<MediaUploadResponse>()),
            text
        )
        return apiResponse.data ?: throw Exception(apiResponse.error?.message ?: "Upload failed")
    }

    suspend fun uploadDocument(bytes: ByteArray, mimeType: String, fileName: String): MediaUploadResponse {
        val response = apiClient.httpClient.post("${ApiClient.BASE_URL}/api/v1/media/document") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        })
                    }
                )
            )
        }
        val text = response.bodyAsText()
        val apiResponse = apiClient.json.decodeFromString(
            ApiResponse.serializer(serializer<MediaUploadResponse>()),
            text
        )
        return apiResponse.data ?: throw Exception(apiResponse.error?.message ?: "Upload failed")
    }

    suspend fun getStorageUsage(): StorageUsageResponse {
        val response = apiClient.httpClient.get("${ApiClient.BASE_URL}/api/v1/media/storage")
        val text = response.bodyAsText()
        val apiResponse = apiClient.json.decodeFromString(
            ApiResponse.serializer(serializer<StorageUsageResponse>()),
            text
        )
        return apiResponse.data ?: throw Exception(apiResponse.error?.message ?: "STORAGE_USAGE_FAILED")
    }

    /**
     * Download a media blob and, when [keyMaterial] is present (recovered from the decrypted message
     * body), verify its integrity hash and AES-GCM-decrypt it. Returns the plaintext bytes ready to
     * render.
     *
     * - [keyMaterial] null → legacy / plaintext media: the bytes are returned as fetched (the flag
     *   was OFF when this media was sent, or it predates media E2E). Byte-identical to today.
     * - [keyMaterial] non-null → ciphertext blob: a tampered blob or wrong key throws
     *   [com.muhabbet.app.crypto.MediaDecryptException] (fail closed) so the UI can surface a visible
     *   decrypt-failed state — it never renders corrupted bytes as if valid.
     */
    suspend fun downloadMedia(url: String, keyMaterial: MediaKeyMaterial? = null): ByteArray {
        val blob = apiClient.httpClient.get(url).bodyAsBytes()
        return mediaEncryptor.decryptDownloaded(blob, keyMaterial)
    }
}

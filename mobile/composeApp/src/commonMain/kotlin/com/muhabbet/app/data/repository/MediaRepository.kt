package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.MediaUploadResponse
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.serializer

class MediaRepository(private val apiClient: ApiClient) {

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
}

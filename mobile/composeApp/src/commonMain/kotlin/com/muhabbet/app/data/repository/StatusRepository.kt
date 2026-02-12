package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.StatusCreateRequest
import com.muhabbet.shared.dto.StatusResponse
import com.muhabbet.shared.dto.UserStatusGroup

class StatusRepository(private val apiClient: ApiClient) {

    suspend fun createStatus(content: String?, mediaUrl: String?): StatusResponse {
        val response = apiClient.post<StatusResponse>(
            "/api/v1/statuses",
            StatusCreateRequest(content = content, mediaUrl = mediaUrl)
        )
        return response.data ?: throw Exception("Failed to create status")
    }

    suspend fun getMyStatuses(): List<StatusResponse> {
        val response = apiClient.get<List<StatusResponse>>("/api/v1/statuses/me")
        return response.data ?: emptyList()
    }

    suspend fun getContactStatuses(): List<UserStatusGroup> {
        val response = apiClient.get<List<UserStatusGroup>>("/api/v1/statuses/contacts")
        return response.data ?: emptyList()
    }

    suspend fun deleteStatus(statusId: String) {
        apiClient.delete("/api/v1/statuses/$statusId")
    }
}

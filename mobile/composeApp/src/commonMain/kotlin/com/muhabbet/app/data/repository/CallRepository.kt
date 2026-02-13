package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.CallHistoryResponse
import com.muhabbet.shared.dto.PaginatedResponse

class CallRepository(private val apiClient: ApiClient) {

    suspend fun getCallHistory(page: Int = 0, size: Int = 20): PaginatedResponse<CallHistoryResponse> {
        val response = apiClient.get<PaginatedResponse<CallHistoryResponse>>(
            "/api/v1/calls/history?page=$page&size=$size"
        )
        return response.data ?: PaginatedResponse(emptyList(), null, false)
    }
}

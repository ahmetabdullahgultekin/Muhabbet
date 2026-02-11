package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.model.Message

class MessageRepository(private val apiClient: ApiClient) {

    suspend fun getMessages(
        conversationId: String,
        cursor: String? = null,
        limit: Int = 50
    ): PaginatedResponse<Message> {
        val path = buildString {
            append("/api/v1/conversations/$conversationId/messages?limit=$limit")
            if (cursor != null) append("&cursor=$cursor")
        }
        val response = apiClient.get<PaginatedResponse<Message>>(path)
        return response.data ?: PaginatedResponse(emptyList(), null, false)
    }
}

package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.ContactSyncRequest
import com.muhabbet.shared.dto.ContactSyncResponse
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.CreateConversationRequest
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.model.ConversationType

class ConversationRepository(private val apiClient: ApiClient) {

    suspend fun getConversations(cursor: String? = null, limit: Int = 20): PaginatedResponse<ConversationResponse> {
        val path = buildString {
            append("/api/v1/conversations?limit=$limit")
            if (cursor != null) append("&cursor=$cursor")
        }
        val response = apiClient.get<PaginatedResponse<ConversationResponse>>(path)
        return response.data ?: PaginatedResponse(emptyList(), null, false)
    }

    suspend fun createDirectConversation(otherUserId: String): ConversationResponse {
        val response = apiClient.post<ConversationResponse>(
            "/api/v1/conversations",
            CreateConversationRequest(
                type = ConversationType.DIRECT,
                participantIds = listOf(otherUserId)
            )
        )
        return response.data ?: throw Exception("Konusma olusturulamadi")
    }

    suspend fun syncContacts(phoneHashes: List<String>): ContactSyncResponse {
        val response = apiClient.post<ContactSyncResponse>(
            "/api/v1/contacts/sync",
            ContactSyncRequest(phoneHashes)
        )
        return response.data ?: ContactSyncResponse(emptyList())
    }
}

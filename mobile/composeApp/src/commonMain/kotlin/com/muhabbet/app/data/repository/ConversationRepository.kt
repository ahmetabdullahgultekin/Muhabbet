package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.ContactSyncRequest
import com.muhabbet.shared.dto.ContactSyncResponse
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.CreateConversationRequest
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.dto.UserProfileDetailResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.UserProfile

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
        return response.data ?: throw Exception(response.error?.message ?: "CONVERSATION_CREATE_FAILED")
    }

    suspend fun syncContacts(phoneHashes: List<String>): ContactSyncResponse {
        val response = apiClient.post<ContactSyncResponse>(
            "/api/v1/contacts/sync",
            ContactSyncRequest(phoneHashes)
        )
        return response.data ?: ContactSyncResponse(emptyList())
    }

    suspend fun deleteConversation(conversationId: String) {
        apiClient.delete<Unit>("/api/v1/conversations/$conversationId")
    }

    suspend fun getUserProfile(userId: String): UserProfile {
        val response = apiClient.get<UserProfile>("/api/v1/users/$userId")
        return response.data ?: throw Exception(response.error?.message ?: "PROFILE_LOAD_FAILED")
    }

    suspend fun getUserProfileDetail(userId: String): UserProfileDetailResponse {
        val response = apiClient.get<UserProfileDetailResponse>("/api/v1/users/$userId/detail")
        return response.data ?: throw Exception(response.error?.message ?: "PROFILE_LOAD_FAILED")
    }

    suspend fun setDisappearTimer(conversationId: String, seconds: Int?) {
        apiClient.put<Unit>("/api/v1/conversations/$conversationId/disappear", mapOf("seconds" to seconds))
    }

    suspend fun pinConversation(conversationId: String) {
        apiClient.put<Unit>("/api/v1/conversations/$conversationId/pin", Unit)
    }

    suspend fun unpinConversation(conversationId: String) {
        apiClient.delete<Unit>("/api/v1/conversations/$conversationId/pin")
    }
}

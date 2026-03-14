package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.CommunityDetailResponse
import com.muhabbet.shared.dto.CommunityResponse
import com.muhabbet.shared.dto.CreateCommunityRequest

class CommunityRepository(
    private val apiClient: ApiClient
) {

    suspend fun getCommunities(): List<CommunityResponse> {
        val response = apiClient.get<List<CommunityResponse>>("/api/v1/communities")
        return response.data ?: emptyList()
    }

    suspend fun getCommunityDetail(communityId: String): CommunityDetailResponse {
        val response = apiClient.get<CommunityDetailResponse>("/api/v1/communities/$communityId")
        return response.data ?: throw Exception("COMMUNITY_LOAD_FAILED")
    }

    suspend fun createCommunity(request: CreateCommunityRequest): CommunityResponse {
        val response = apiClient.post<CommunityResponse>("/api/v1/communities", request)
        return response.data ?: throw Exception("COMMUNITY_CREATE_FAILED")
    }

    suspend fun addGroupToCommunity(communityId: String, conversationId: String) {
        apiClient.post<Unit>(
            "/api/v1/communities/$communityId/groups",
            mapOf("conversationId" to conversationId)
        )
    }

    suspend fun removeGroupFromCommunity(communityId: String, conversationId: String) {
        apiClient.delete<Unit>("/api/v1/communities/$communityId/groups/$conversationId")
    }
}

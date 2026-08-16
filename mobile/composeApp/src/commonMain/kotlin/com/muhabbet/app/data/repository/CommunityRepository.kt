package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.CommunityDetailResponse
import com.muhabbet.shared.dto.CommunityMemberCandidateResponse
import com.muhabbet.shared.dto.CommunityMemberResponse
import com.muhabbet.shared.dto.CommunityResponse
import com.muhabbet.shared.dto.CreateCommunityRequest
import com.muhabbet.shared.dto.UpdateCommunityRequest

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

    /**
     * Everyone in the community. Members only server-side, so a non-member gets an [ApiException]
     * rather than an empty list — the screen must show the failure, not an empty community.
     */
    suspend fun getCommunityMembers(communityId: String): List<CommunityMemberResponse> {
        val response = apiClient.get<List<CommunityMemberResponse>>("/api/v1/communities/$communityId/members")
        return response.data ?: emptyList()
    }

    /**
     * The people the server will currently accept into this community: members of its own groups
     * who are not in it yet. Deliberately not a contact list — since #375 an add outside that set
     * is refused, so offering one would be offering a button that always fails.
     */
    suspend fun getAddableUsers(communityId: String): List<CommunityMemberCandidateResponse> {
        val response = apiClient.get<List<CommunityMemberCandidateResponse>>(
            "/api/v1/communities/$communityId/member-candidates"
        )
        return response.data ?: emptyList()
    }

    suspend fun addMemberToCommunity(communityId: String, userId: String) {
        apiClient.post<Unit>("/api/v1/communities/$communityId/members", mapOf("userId" to userId))
    }

    suspend fun updateCommunity(communityId: String, name: String, description: String?): CommunityResponse {
        val response = apiClient.patch<CommunityResponse>(
            "/api/v1/communities/$communityId",
            UpdateCommunityRequest(name = name, description = description)
        )
        return response.data ?: throw Exception("COMMUNITY_UPDATE_FAILED")
    }

    suspend fun leaveCommunity(communityId: String) {
        apiClient.post<Unit>("/api/v1/communities/$communityId/leave", emptyMap<String, String>())
    }
}

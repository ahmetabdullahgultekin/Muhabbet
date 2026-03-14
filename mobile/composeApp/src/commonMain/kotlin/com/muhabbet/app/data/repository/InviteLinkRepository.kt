package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.CreateInviteLinkRequest
import com.muhabbet.shared.dto.InviteLinkResponse

class InviteLinkRepository(
    private val apiClient: ApiClient
) {

    suspend fun getInviteLink(conversationId: String): InviteLinkResponse? {
        return try {
            val response = apiClient.get<InviteLinkResponse>(
                "/api/v1/conversations/$conversationId/invite-link"
            )
            response.data
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createInviteLink(
        conversationId: String,
        request: CreateInviteLinkRequest
    ): InviteLinkResponse {
        val response = apiClient.post<InviteLinkResponse>(
            "/api/v1/conversations/$conversationId/invite-link",
            request
        )
        return response.data ?: throw Exception("INVITE_LINK_CREATE_FAILED")
    }

    suspend fun revokeInviteLink(conversationId: String, linkId: String) {
        apiClient.delete<Unit>(
            "/api/v1/conversations/$conversationId/invite-link/$linkId"
        )
    }
}

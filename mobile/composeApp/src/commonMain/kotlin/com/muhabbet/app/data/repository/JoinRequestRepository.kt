package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.JoinRequestResponse

/**
 * Invite-link join requests, from the admin's side (#788).
 *
 * `JoinRequestController` had list/approve/reject for as long as invite links have existed and no
 * client called any of them, so a request made through a link waited forever. The server allows
 * all three to a group's admins and owner only.
 */
class JoinRequestRepository(private val apiClient: ApiClient) {

    suspend fun pending(conversationId: String): List<JoinRequestResponse> {
        val response = apiClient.get<List<JoinRequestResponse>>(basePath(conversationId))
        return response.data ?: throw Exception(response.error?.code ?: "JOIN_REQUEST_LIST_FAILED")
    }

    suspend fun approve(conversationId: String, requestId: String) {
        apiClient.post<Unit>("${basePath(conversationId)}/$requestId/approve", Unit)
    }

    suspend fun reject(conversationId: String, requestId: String) {
        apiClient.post<Unit>("${basePath(conversationId)}/$requestId/reject", Unit)
    }

    private fun basePath(conversationId: String) = "/api/v1/conversations/$conversationId/join-requests"
}

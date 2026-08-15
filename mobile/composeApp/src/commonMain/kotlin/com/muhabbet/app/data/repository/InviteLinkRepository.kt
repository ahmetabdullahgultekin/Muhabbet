package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.shared.dto.CreateInviteLinkRequest
import com.muhabbet.shared.dto.InviteLinkResponse
import io.ktor.http.HttpStatusCode

class InviteLinkRepository(
    private val apiClient: ApiClient
) {

    /**
     * The group's current invite link, or null when it has none.
     *
     * A group with no link answers 404, which is the normal state of a group nobody has shared yet
     * — so that one status is absorbed into null. Every other failure propagates: this used to
     * catch them all and return null, which rendered a 403 as "no link yet, create one" and put a
     * Create button in front of a user who is not allowed to press it.
     */
    suspend fun getInviteLink(conversationId: String): InviteLinkResponse? =
        try {
            apiClient.get<InviteLinkResponse>(
                "/api/v1/conversations/$conversationId/invite-link"
            ).data
        } catch (e: ApiException) {
            if (e.status == HttpStatusCode.NotFound.value) null else throw e
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

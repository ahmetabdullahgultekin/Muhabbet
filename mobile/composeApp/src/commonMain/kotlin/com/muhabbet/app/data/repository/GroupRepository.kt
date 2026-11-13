package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.requireData
import com.muhabbet.shared.dto.AddMembersRequest
import com.muhabbet.shared.dto.AnnouncementModeResponse
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.CreateConversationRequest
import com.muhabbet.shared.dto.EditMessageRequest
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.dto.SetAnnouncementModeRequest
import com.muhabbet.shared.dto.UpdateGroupRequest
import com.muhabbet.shared.dto.UpdateRoleRequest
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.MemberRole
import kotlinx.serialization.json.JsonElement

class GroupRepository(private val apiClient: ApiClient) {

    suspend fun createGroup(name: String, participantIds: List<String>): ConversationResponse {
        val response = apiClient.post<ConversationResponse>(
            "/api/v1/conversations",
            CreateConversationRequest(
                type = ConversationType.GROUP,
                participantIds = participantIds,
                name = name
            )
        )
        return response.requireData()
    }

    suspend fun addMembers(conversationId: String, userIds: List<String>): List<ParticipantResponse> {
        val response = apiClient.post<List<ParticipantResponse>>(
            "/api/v1/conversations/$conversationId/members",
            AddMembersRequest(userIds)
        )
        return response.data ?: emptyList()
    }

    suspend fun removeMember(conversationId: String, userId: String) {
        apiClient.delete<Unit>("/api/v1/conversations/$conversationId/members/$userId")
    }

    /** A null field leaves that part of the group untouched, so callers may send just one. */
    suspend fun updateGroupInfo(
        conversationId: String,
        name: String? = null,
        description: String? = null,
        avatarUrl: String? = null
    ): ConversationResponse {
        val response = apiClient.patch<ConversationResponse>(
            "/api/v1/conversations/$conversationId",
            UpdateGroupRequest(name = name, description = description, avatarUrl = avatarUrl)
        )
        return response.requireData()
    }

    /**
     * Turns "only admins may post" on or off, returning what the server stored (#509).
     *
     * The group screen used to build this call itself: `PATCH /conversations/{id}` with an ad-hoc
     * `mapOf("announcementOnly" to enabled)`. That route's body is [UpdateGroupRequest], which has
     * no such field, so the server discarded it and answered 200 — the switch moved, the group
     * stayed open, and nobody found out until it was spammed. It goes through the shared
     * [SetAnnouncementModeRequest] here so a rename on either side breaks the build instead.
     *
     * The return value is the server's, not the caller's argument: the switch must not claim a
     * permission change the server did not make.
     */
    suspend fun setAnnouncementMode(conversationId: String, enabled: Boolean): Boolean {
        val response = apiClient.put<AnnouncementModeResponse>(
            "/api/v1/conversations/$conversationId/announcement",
            SetAnnouncementModeRequest(enabled = enabled)
        )
        return response.requireData().announcementOnly
    }

    suspend fun updateMemberRole(conversationId: String, userId: String, role: MemberRole) {
        apiClient.patch<Unit>(
            "/api/v1/conversations/$conversationId/members/$userId/role",
            UpdateRoleRequest(role)
        )
    }

    suspend fun leaveGroup(conversationId: String) {
        apiClient.post<Unit>("/api/v1/conversations/$conversationId/leave", Unit)
    }

    suspend fun deleteMessage(messageId: String) {
        apiClient.delete<Unit>("/api/v1/messages/$messageId")
    }

    /**
     * Through the checked `patch` like every other call (#798), so a refusal is an `ApiException`
     * with the server's code rather than a bare `MESSAGE_EDIT_FAILED`. Decoded as [JsonElement]: the
     * caller has already updated its list optimistically and needs nothing back, and an untyped
     * decode keeps a change to the edited message's shape from failing an edit the server accepted.
     */
    suspend fun editMessage(messageId: String, content: String) {
        apiClient.patch<JsonElement>("/api/v1/messages/$messageId", EditMessageRequest(content))
    }
}

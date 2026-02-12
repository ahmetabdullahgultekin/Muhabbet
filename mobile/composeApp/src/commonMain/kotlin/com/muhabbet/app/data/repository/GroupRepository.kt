package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.AddMembersRequest
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.CreateConversationRequest
import com.muhabbet.shared.dto.EditMessageRequest
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.dto.UpdateGroupRequest
import com.muhabbet.shared.dto.UpdateRoleRequest
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.MemberRole
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.contentType
import io.ktor.http.isSuccess

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
        return response.data ?: throw Exception("Grup oluşturulamadı")
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

    suspend fun updateGroupInfo(conversationId: String, name: String?, description: String?): ConversationResponse {
        val response = apiClient.patch<ConversationResponse>(
            "/api/v1/conversations/$conversationId",
            UpdateGroupRequest(name = name, description = description)
        )
        return response.data ?: throw Exception("Grup güncellenemedi")
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

    suspend fun editMessage(messageId: String, content: String) {
        val response = apiClient.httpClient.patch("${ApiClient.BASE_URL}/api/v1/messages/$messageId") {
            setBody(EditMessageRequest(content))
            contentType(io.ktor.http.ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            throw Exception("Mesaj düzenlenemedi")
        }
    }
}

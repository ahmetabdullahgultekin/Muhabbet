package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.ConversationCache
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.app.util.Log
import com.muhabbet.shared.dto.ContactSyncRequest
import com.muhabbet.shared.dto.ContactSyncResponse
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.CreateConversationRequest
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.dto.UserProfileDetailResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.UserProfile
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.http.encodeURLParameter

class ConversationRepository(
    private val apiClient: ApiClient,
    private val localCache: ConversationCache
) {

    private companion object {
        const val TAG = "ConversationRepository"
    }

    suspend fun getConversations(cursor: String? = null, limit: Int = 20): PaginatedResponse<ConversationResponse> {
        return try {
            val path = buildString {
                append("/api/v1/conversations?limit=$limit")
                // Opaque and server-minted, so encoded rather than trusted — see MessageRepository.
                if (cursor != null) append("&cursor=${cursor.encodeURLParameter()}")
            }
            val response = apiClient.get<PaginatedResponse<ConversationResponse>>(path)
            val result = response.data ?: PaginatedResponse(emptyList(), null, false)
            // Write through to cache on first page
            if (cursor == null) {
                localCache.upsertConversations(result.items)
            } else {
                localCache.upsertConversations(result.items)
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            // The cache answers an UNREACHABLE server, not one that answered and said no. Serving
            // it here would hide a 403 behind data the user has no reason to doubt — the same
            // failure-that-looks-like-success this whole change exists to remove.
            Log.e(TAG, "Conversation fetch rejected by the server: $e")
            throw e
        } catch (e: Exception) {
            // Genuinely offline: stale beats blank, but never silent in the log.
            Log.w(TAG, "Conversation fetch failed, falling back to cache: $e")
            if (cursor == null) {
                val cached = localCache.getConversations()
                if (cached.isNotEmpty()) {
                    PaginatedResponse(cached, null, false)
                } else throw e
            } else throw e
        }
    }

    suspend fun createDirectConversation(otherUserId: String): ConversationResponse {
        val response = apiClient.post<ConversationResponse>(
            "/api/v1/conversations",
            CreateConversationRequest(
                type = ConversationType.DIRECT,
                participantIds = listOf(otherUserId)
            )
        )
        val conv = response.data ?: throw Exception(response.error?.message ?: "CONVERSATION_CREATE_FAILED")
        localCache.upsertConversation(conv)
        return conv
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
        localCache.deleteConversation(conversationId)
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

    suspend fun archiveConversation(conversationId: String) {
        apiClient.put<Unit>("/api/v1/conversations/$conversationId/archive", Unit)
    }

    suspend fun unarchiveConversation(conversationId: String) {
        apiClient.delete<Unit>("/api/v1/conversations/$conversationId/archive")
    }

    suspend fun muteConversation(conversationId: String, duration: String) {
        apiClient.put<Unit>(
            "/api/v1/conversations/$conversationId/mute",
            com.muhabbet.shared.dto.MuteRequest(duration)
        )
    }

    suspend fun unmuteConversation(conversationId: String) {
        apiClient.delete<Unit>("/api/v1/conversations/$conversationId/mute")
    }

    suspend fun lockConversation(conversationId: String) {
        apiClient.put<Unit>("/api/v1/conversations/$conversationId/lock", Unit)
    }

    suspend fun unlockConversation(conversationId: String) {
        apiClient.delete<Unit>("/api/v1/conversations/$conversationId/lock")
    }
}

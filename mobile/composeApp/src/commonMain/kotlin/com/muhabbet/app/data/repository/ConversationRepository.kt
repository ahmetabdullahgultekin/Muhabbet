package com.muhabbet.app.data.repository

import com.muhabbet.app.data.DefaultDispatcherProvider
import com.muhabbet.app.data.DispatcherProvider
import com.muhabbet.app.data.local.ConversationCache
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.DataException
import com.muhabbet.app.data.remote.isConnectivity
import com.muhabbet.app.data.remote.requireData
import com.muhabbet.app.util.Log
import com.muhabbet.shared.dto.ContactSyncRequest
import com.muhabbet.shared.dto.ContactSyncResponse
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.CreateConversationRequest
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.dto.UserProfileDetailResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.UserProfile
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.withContext

class ConversationRepository(
    private val apiClient: ApiClient,
    private val localCache: ConversationCache,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
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
            // Write through to the cache, off the caller's thread (#820).
            withContext(dispatchers.io) { localCache.upsertConversations(result.items) }
            result
        } catch (e: DataException) {
            // The cache answers an UNREACHABLE server, not one that answered and said no. Serving
            // it for a 403 would hide the refusal behind data the user has no reason to doubt —
            // the failure-that-looks-like-success #374 removed. Only connectivity falls back.
            if (!e.error.isConnectivity || cursor != null) {
                Log.e(TAG, "Conversation fetch failed: $e")
                throw e
            }
            // Genuinely offline: stale beats blank, but never silent in the log.
            Log.w(TAG, "Conversation fetch failed, falling back to cache: $e")
            val cached = withContext(dispatchers.io) { localCache.getConversations() }
            if (cached.isNotEmpty()) PaginatedResponse(cached, null, false) else throw e
        }
    }

    /**
     * One conversation by id, wherever it sits in the user's history (#800).
     *
     * Chat and Group Info used to find their conversation by scanning the first page of the list,
     * so anything past the twentieth chat opened with no members, no roles, no timer and no
     * announcement flag. This asks the server for exactly that conversation — with each member's
     * real role, which the list does not carry — and writes it through to the cache.
     *
     * Offline, the cached copy answers instead, if there is one. The cache does not store every
     * flag (announcement-only, mute, archive), so a cached answer carries their defaults; that only
     * ever under-offers, and the server re-checks every privileged action regardless.
     *
     * @throws DataException `NotFound` when the conversation does not exist or the caller is not in
     *   it — the server answers both the same way on purpose.
     */
    suspend fun getConversation(conversationId: String): ConversationResponse {
        return try {
            val conversation = apiClient
                .get<ConversationResponse>("/api/v1/conversations/$conversationId")
                .requireData()
            withContext(dispatchers.io) { localCache.upsertConversation(conversation) }
            conversation
        } catch (e: DataException) {
            if (!e.error.isConnectivity) throw e
            Log.w(TAG, "Conversation $conversationId fetch failed, falling back to cache: $e")
            withContext(dispatchers.io) { localCache.getConversation(conversationId) } ?: throw e
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
        val conv = response.requireData()
        withContext(dispatchers.io) { localCache.upsertConversation(conv) }
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
        withContext(dispatchers.io) { localCache.deleteConversation(conversationId) }
    }

    suspend fun getUserProfile(userId: String): UserProfile {
        val response = apiClient.get<UserProfile>("/api/v1/users/$userId")
        return response.requireData()
    }

    suspend fun getUserProfileDetail(userId: String): UserProfileDetailResponse {
        val response = apiClient.get<UserProfileDetailResponse>("/api/v1/users/$userId/detail")
        return response.requireData()
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

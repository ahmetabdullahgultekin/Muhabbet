package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.LocalCache
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.MessageInfoResponse
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.dto.PollResultResponse
import com.muhabbet.shared.dto.PollVoteRequest
import com.muhabbet.shared.dto.ReactionRequest
import com.muhabbet.shared.dto.ReactionResponse
import com.muhabbet.shared.model.Message

class MessageRepository(
    private val apiClient: ApiClient,
    private val localCache: LocalCache
) {

    suspend fun getMessages(
        conversationId: String,
        cursor: String? = null,
        limit: Int = 50
    ): PaginatedResponse<Message> {
        return try {
            val path = buildString {
                append("/api/v1/conversations/$conversationId/messages?limit=$limit")
                if (cursor != null) append("&cursor=$cursor")
            }
            val response = apiClient.get<PaginatedResponse<Message>>(path)
            val result = response.data ?: PaginatedResponse(emptyList(), null, false)
            // Cache messages
            localCache.upsertMessages(result.items)
            result
        } catch (e: Exception) {
            // Fallback to cache on network failure
            if (cursor == null) {
                val cached = localCache.getMessagesByPage(conversationId, limit)
                if (cached.isNotEmpty()) {
                    PaginatedResponse(cached, null, false)
                } else throw e
            } else throw e
        }
    }

    suspend fun starMessage(messageId: String) {
        apiClient.post<Unit>("/api/v1/starred/$messageId", Unit)
    }

    suspend fun unstarMessage(messageId: String) {
        apiClient.delete<Unit>("/api/v1/starred/$messageId")
    }

    suspend fun getStarredMessages(limit: Int = 50, offset: Int = 0): PaginatedResponse<Message> {
        val response = apiClient.get<PaginatedResponse<Message>>("/api/v1/starred?limit=$limit&offset=$offset")
        return response.data ?: PaginatedResponse(emptyList(), null, false)
    }

    suspend fun searchMessages(query: String, conversationId: String? = null, limit: Int = 30): PaginatedResponse<Message> {
        val path = buildString {
            append("/api/v1/search/messages?q=$query&limit=$limit")
            if (conversationId != null) append("&conversationId=$conversationId")
        }
        val response = apiClient.get<PaginatedResponse<Message>>(path)
        return response.data ?: PaginatedResponse(emptyList(), null, false)
    }

    suspend fun votePoll(messageId: String, optionIndex: Int): PollResultResponse {
        val response = apiClient.post<PollResultResponse>(
            "/api/v1/polls/$messageId/vote",
            PollVoteRequest(optionIndex)
        )
        return response.data ?: throw Exception("Vote failed")
    }

    suspend fun getPollResults(messageId: String): PollResultResponse {
        val response = apiClient.get<PollResultResponse>("/api/v1/polls/$messageId/results")
        return response.data ?: throw Exception("Failed to load poll results")
    }

    suspend fun addReaction(messageId: String, emoji: String) {
        apiClient.post<Unit>("/api/v1/messages/$messageId/reactions", ReactionRequest(emoji))
    }

    suspend fun removeReaction(messageId: String, emoji: String) {
        apiClient.delete<Unit>("/api/v1/messages/$messageId/reactions/$emoji")
    }

    suspend fun getReactions(messageId: String): List<ReactionResponse> {
        val response = apiClient.get<List<ReactionResponse>>("/api/v1/messages/$messageId/reactions")
        return response.data ?: emptyList()
    }

    suspend fun getMediaMessages(conversationId: String, limit: Int = 50, offset: Int = 0): PaginatedResponse<Message> {
        val response = apiClient.get<PaginatedResponse<Message>>("/api/v1/conversations/$conversationId/media?limit=$limit&offset=$offset")
        return response.data ?: PaginatedResponse(emptyList(), null, false)
    }

    suspend fun getMessageInfo(messageId: String): MessageInfoResponse {
        val response = apiClient.get<MessageInfoResponse>("/api/v1/messages/$messageId/info")
        return response.data ?: throw Exception("Failed to load message info")
    }

    /**
     * Sync messages since a given timestamp.
     * Used by background sync to catch up on missed messages.
     * Returns the list of synced messages and caches them locally.
     */
    suspend fun syncMessagesSince(timestamp: String): List<Message> {
        val response = apiClient.get<PaginatedResponse<Message>>(
            "/api/v1/messages/since?timestamp=$timestamp"
        )
        val messages = response.data?.items ?: emptyList()
        if (messages.isNotEmpty()) {
            localCache.upsertMessages(messages)
        }
        return messages
    }
}

package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.MessageCache
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.generateMessageId
import com.muhabbet.shared.dto.MessageInfoResponse
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.dto.PollResultResponse
import com.muhabbet.shared.dto.PollVoteRequest
import com.muhabbet.shared.dto.ReactionRequest
import com.muhabbet.shared.dto.ReactionResponse
import com.muhabbet.shared.dto.SendMessageRequest
import com.muhabbet.shared.dto.ViewOnceRevealResponse
import com.muhabbet.shared.model.Message
import kotlin.coroutines.cancellation.CancellationException

class MessageRepository(
    private val apiClient: ApiClient,
    private val localCache: MessageCache
) {

    private companion object {
        const val TAG = "MessageRepository"
    }

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            // The cache answers an UNREACHABLE server, not one that answered and said no. Serving
            // it here would hide a 403 behind data the user has no reason to doubt — the same
            // failure-that-looks-like-success this whole change exists to remove.
            Log.e(TAG, "Message fetch rejected by the server: $e")
            throw e
        } catch (e: Exception) {
            // Genuinely offline: stale beats blank, but never silent in the log.
            Log.w(TAG, "Message fetch failed, falling back to cache: $e")
            if (cursor == null) {
                val cached = localCache.getMessagesByPage(conversationId, limit)
                if (cached.isNotEmpty()) {
                    PaginatedResponse(cached, null, false)
                } else throw e
            } else throw e
        }
    }

    /**
     * Sends one text message over REST, for callers with no WebSocket to reach for.
     *
     * The socket stays the normal path — it is already open while a chat is on screen, and it is
     * the only path that gets a `ServerAck` back. This exists for the notification inline reply
     * (#510), which runs in a `BroadcastReceiver` that may be the only thing alive in the process
     * and has about ten seconds before the system reclaims it; bringing a socket up, authenticating
     * it and tearing it down again inside that budget is a worse bet than one POST.
     *
     * Throws — [ApiException] when the server rejected it, anything else when it never arrived — so
     * that a caller cannot mistake a failure for a send. That is the whole point of the issue this
     * came from: the notification used to report success unconditionally.
     */
    suspend fun sendMessage(conversationId: String, content: String): Message {
        val response = apiClient.post<Message>(
            "/api/v1/conversations/$conversationId/messages",
            // Generated here, once per send: the server rejects a repeat of an id it has already
            // stored, so this is what stops a retried POST from posting the message twice.
            SendMessageRequest(messageId = generateMessageId(), content = content)
        )
        val sent = response.data ?: throw Exception("MSG_SEND_FAILED")
        // Cached so the message is on screen the moment the app is opened, rather than only after
        // the first history fetch comes back.
        localCache.upsertMessages(listOf(sent))
        return sent
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
    /**
     * Opens a view-once message: burns it server-side and returns the media it released.
     *
     * The URL arrives only here. Every list, search and socket payload nulls `mediaUrl` for a
     * view-once message, so this call is the one moment the photo exists on the recipient's device —
     * which is what makes "once" enforceable rather than a label on a bubble. A second call, from
     * this device or another, fails with `MSG_VIEW_ONCE_ALREADY_VIEWED` and releases nothing.
     */
    suspend fun revealViewOnce(messageId: String): ViewOnceRevealResponse {
        val response = apiClient.post<ViewOnceRevealResponse>("/api/v1/messages/$messageId/view-once", Unit)
        return response.data ?: throw Exception("VIEW_ONCE_REVEAL_FAILED")
    }

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

package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.shared.dto.PinMessageRequest
import com.muhabbet.shared.dto.PinnedMessageInfo

/**
 * Pin-in-chat — the client half of a backend vertical that shipped with no client at all (#790).
 *
 * The server keeps at most three pins per conversation and tells every member the new set as a
 * `message.pinned` snapshot after each change, so a screen holds the list it loaded here and
 * replaces it wholesale from the frame; there is no per-pin bookkeeping to drift.
 */
class PinnedMessageRepository(
    private val apiClient: ApiClient
) {

    companion object {
        /** `ErrorCode.MSG_PIN_LIMIT_REACHED`, answered with 409 on a fourth pin. */
        internal const val PIN_LIMIT_REACHED = "MSG_PIN_LIMIT_REACHED"

        internal fun pinsPath(conversationId: String) = "/api/v1/conversations/$conversationId/pinned-messages"
    }

    /** The conversation's pins, oldest first. */
    suspend fun getPinned(conversationId: String): List<PinnedMessageInfo> =
        apiClient.get<List<PinnedMessageInfo>>(pinsPath(conversationId)).data ?: emptyList()

    /**
     * Pins [messageId]. Re-pinning a pinned message is a success that returns the existing pin.
     *
     * The limit comes back as a [PinResult] rather than an exception because it is an expected
     * answer the screen must word for the user, not a failure; every other rejection still throws
     * [ApiException], as everywhere else in the data layer.
     */
    suspend fun pin(conversationId: String, messageId: String): PinResult = try {
        val pin = apiClient.post<PinnedMessageInfo>(pinsPath(conversationId), PinMessageRequest(messageId)).data
            ?: throw IllegalStateException("MSG_PIN_FAILED")
        PinResult.Pinned(pin)
    } catch (e: ApiException) {
        if (e.code == PIN_LIMIT_REACHED) PinResult.LimitReached else throw e
    }

    suspend fun unpin(conversationId: String, messageId: String) {
        apiClient.delete<Unit>("${pinsPath(conversationId)}/$messageId")
    }
}

sealed interface PinResult {
    data class Pinned(val pin: PinnedMessageInfo) : PinResult

    /** The conversation already has the maximum number of pins; one must be unpinned first. */
    data object LimitReached : PinResult
}

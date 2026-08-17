package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.BlockedUserResponse
import com.muhabbet.shared.dto.CreateReportRequest

/**
 * Block, unblock, list-blocked and report — the moderation endpoints, in one place.
 *
 * This class did not exist before #613. Both buttons on `UserProfileScreen` that this backs —
 * "Block" and "Report" — showed a success snackbar and called nothing: `onConfirm` ran
 * `snackbarHostState.showSnackbar(...)` and set the dialog's visibility flag, with no repository
 * call in between. Blocking looked done because the confirmation looked done. It is the actual
 * reason a blocked-list screen was never built on top of it: a list built on a Block button that
 * persists nothing would open empty for every user who used the button rather than a raw API call,
 * which is the #378 shape this whole codebase now checks for explicitly.
 */
class ModerationRepository(
    private val apiClient: ApiClient
) {

    companion object {
        internal const val BASE_PATH = "/api/v1/moderation"
        internal const val BLOCKS_PATH = "$BASE_PATH/blocks"
        internal const val REPORTS_PATH = "$BASE_PATH/reports"
    }

    /** No request body — the target is the path segment and the caller is the JWT. */
    suspend fun blockUser(userId: String) {
        apiClient.post<Unit>("$BLOCKS_PATH/$userId", Unit)
    }

    suspend fun unblockUser(userId: String) {
        apiClient.delete<Unit>("$BLOCKS_PATH/$userId")
    }

    /** Whether the signed-in user has blocked [userId] — backs the Block/Unblock toggle on a profile. */
    suspend fun isBlocked(userId: String): Boolean =
        apiClient.get<Map<String, Boolean>>("$BLOCKS_PATH/$userId").data?.get("blocked") ?: false

    /**
     * The caller's own block list, newest first, each row already carrying a [BlockedUserResponse]
     * with a `displayName`/`avatarUrl` resolved server-side — see `ModerationController`. Missing
     * `data` (an empty-body 2xx) reads as "nobody blocked" rather than throwing; a real rejection
     * still throws via [com.muhabbet.app.data.remote.ApiException], same as every other call here.
     */
    suspend fun getBlockedUsers(): List<BlockedUserResponse> =
        apiClient.get<List<BlockedUserResponse>>(BLOCKS_PATH).data ?: emptyList()

    /**
     * Submits a report. [reason] must be one of the backend's `ReportReason` names — there is no
     * reason picker in the UI yet, so `UserProfileScreen`'s single "Report" action always sends
     * `"OTHER"`. Adding a reason picker is a UI feature, not part of making this button real, and is
     * left for whoever builds that screen.
     */
    suspend fun reportUser(
        reportedUserId: String? = null,
        reportedMessageId: String? = null,
        reportedConversationId: String? = null,
        reason: String = "OTHER",
        description: String? = null
    ) {
        apiClient.post<Unit>(
            REPORTS_PATH,
            CreateReportRequest(
                reportedUserId = reportedUserId,
                reportedMessageId = reportedMessageId,
                reportedConversationId = reportedConversationId,
                reason = reason,
                description = description
            )
        )
    }
}

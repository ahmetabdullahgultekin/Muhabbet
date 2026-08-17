package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Status
import java.util.UUID

interface ManageStatusUseCase {
    fun createStatus(userId: UUID, content: String?, mediaUrl: String?): Status
    fun createStatusWithAudience(
        userId: UUID,
        content: String?,
        mediaUrl: String?,
        visibility: String,
        excludedUserIds: List<UUID>,
        includedUserIds: List<UUID>
    ): Status
    fun getMyStatuses(userId: UUID): List<Status>

    /**
     * The statuses [viewerUserId] is entitled to see, grouped by author and carrying that author's
     * name so no caller has to invent a label for a user id.
     *
     * There used to be an unscoped `getContactStatuses()` alongside this one that returned every
     * status on the instance. It was dead, and it was the shape of the bug in #507 — it has been
     * removed rather than left as a second, wrong way to ask the same question.
     */
    fun getContactStatusesForUser(viewerUserId: UUID): List<StatusGroup>

    fun deleteStatus(statusId: UUID, userId: UUID)
}

data class StatusGroup(
    val userId: UUID,
    val statuses: List<Status>,
    /** Resolved server-side: the client must never fall back to rendering the raw user id. */
    val displayName: String? = null,
    val avatarUrl: String? = null
)

package com.muhabbet.moderation.domain.port.`in`

import com.muhabbet.moderation.domain.model.ReportReason
import com.muhabbet.moderation.domain.model.UserBlock
import com.muhabbet.moderation.domain.model.UserReport
import java.util.UUID

interface ReportUserUseCase {
    fun reportUser(
        reporterId: UUID,
        reportedUserId: UUID?,
        reportedMessageId: UUID?,
        reportedConversationId: UUID?,
        reason: ReportReason,
        description: String?
    ): UserReport
}

interface BlockUserUseCase {
    fun blockUser(blockerId: UUID, blockedId: UUID)
    fun unblockUser(blockerId: UUID, blockedId: UUID)

    /**
     * Every block [userId] has placed, newest first at the repository layer's discretion — the
     * caller (currently `ModerationController`) is the one that owns display ordering.
     *
     * Returns the domain model rather than a bare `List<UUID>` deliberately: `createdAt` is what
     * lets a blocked-list screen say *when*, and there was previously nowhere for that to survive
     * past this call — the old signature threw it away here and the controller had nothing to
     * re-derive it from.
     */
    fun getBlockedUsers(userId: UUID): List<UserBlock>
    fun isBlocked(blockerId: UUID, blockedId: UUID): Boolean

    /**
     * Which of [candidateIds] have blocked [userId] — the reverse direction of [getBlockedUsers],
     * and the question presence has to ask. Batched by contract: a conversation list resolves every
     * participant on the page in one query, never one call per participant.
     */
    fun findBlockersAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID>

    /**
     * Which of [candidateIds] [userId] has blocked — [getBlockedUsers] asked about a specific set
     * instead of the whole list, and the question a feed has to ask before it shows anyone.
     *
     * Not served by filtering [getBlockedUsers] in the caller: that reads every block the user has
     * ever placed in order to keep the handful that are on screen, and it hands a domain model to
     * a caller that only wants ids. Batched by contract, like its mirror above.
     */
    fun findBlockedAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID>
}

interface ReviewReportsUseCase {
    fun getPendingReports(limit: Int, offset: Int): List<UserReport>
    fun resolveReport(reportId: UUID, reviewerId: UUID, dismiss: Boolean)
}

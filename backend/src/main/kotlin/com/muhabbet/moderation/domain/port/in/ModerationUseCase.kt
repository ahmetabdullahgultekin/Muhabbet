package com.muhabbet.moderation.domain.port.`in`

import com.muhabbet.moderation.domain.model.ReportReason
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
    fun getBlockedUsers(userId: UUID): List<UUID>
    fun isBlocked(blockerId: UUID, blockedId: UUID): Boolean
}

interface ReviewReportsUseCase {
    fun getPendingReports(limit: Int, offset: Int): List<UserReport>
    fun resolveReport(reportId: UUID, reviewerId: UUID, dismiss: Boolean)
}

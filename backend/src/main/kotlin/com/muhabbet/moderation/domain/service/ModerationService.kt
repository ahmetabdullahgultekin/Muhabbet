package com.muhabbet.moderation.domain.service

import com.muhabbet.moderation.domain.model.ReportReason
import com.muhabbet.moderation.domain.model.ReportStatus
import com.muhabbet.moderation.domain.model.UserBlock
import com.muhabbet.moderation.domain.model.UserReport
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReportUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReviewReportsUseCase
import com.muhabbet.moderation.domain.port.out.BlockRepository
import com.muhabbet.moderation.domain.port.out.ReportRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.util.UUID

class ModerationService(
    private val reportRepository: ReportRepository,
    private val blockRepository: BlockRepository,
    private val transactions: TransactionRunner
) : ReportUserUseCase, BlockUserUseCase, ReviewReportsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun reportUser(
        reporterId: UUID,
        reportedUserId: UUID?,
        reportedMessageId: UUID?,
        reportedConversationId: UUID?,
        reason: ReportReason,
        description: String?
    ): UserReport = transactions.inTransaction {
        val report = reportRepository.save(
            UserReport(
                reporterId = reporterId,
                reportedUserId = reportedUserId,
                reportedMessageId = reportedMessageId,
                reportedConversationId = reportedConversationId,
                reason = reason,
                description = description
            )
        )
        log.info("Report created: id={}, reporter={}, reason={}", report.id, reporterId, reason)
        return@inTransaction report
    }

    override fun blockUser(blockerId: UUID, blockedId: UUID) {
        transactions.inTransaction<Unit> {
            if (blockerId == blockedId) {
                throw BusinessException(ErrorCode.BLOCK_SELF)
            }
            if (!blockRepository.exists(blockerId, blockedId)) {
                blockRepository.save(UserBlock(blockerId = blockerId, blockedId = blockedId))
                log.info("User {} blocked {}", blockerId, blockedId)
            }
        }
    }

    override fun unblockUser(blockerId: UUID, blockedId: UUID) {
        transactions.inTransaction<Unit> {
            blockRepository.delete(blockerId, blockedId)
            log.info("User {} unblocked {}", blockerId, blockedId)
        }
    }

    override fun getBlockedUsers(userId: UUID): List<UserBlock> = transactions.inReadOnlyTransaction {
        return@inReadOnlyTransaction blockRepository.findByBlockerId(userId)
    }

    override fun isBlocked(blockerId: UUID, blockedId: UUID): Boolean = transactions.inReadOnlyTransaction {
        return@inReadOnlyTransaction blockRepository.exists(blockerId, blockedId)
    }

    override fun findBlockersAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> = transactions.inReadOnlyTransaction {
        return@inReadOnlyTransaction blockRepository.findBlockersAmong(userId, candidateIds)
    }

    override fun findBlockedAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> = transactions.inReadOnlyTransaction {
        return@inReadOnlyTransaction blockRepository.findBlockedAmong(userId, candidateIds)
    }

    override fun getPendingReports(limit: Int, offset: Int): List<UserReport> = transactions.inReadOnlyTransaction {
        return@inReadOnlyTransaction reportRepository.findByStatus(ReportStatus.PENDING, limit.coerceIn(1, 100), offset.coerceAtLeast(0))
    }

    override fun resolveReport(reportId: UUID, reviewerId: UUID, dismiss: Boolean) {
        transactions.inTransaction<Unit> {
            val report = reportRepository.findById(reportId)
                ?: throw BusinessException(ErrorCode.REPORT_NOT_FOUND)
            val newStatus = if (dismiss) ReportStatus.DISMISSED else ReportStatus.RESOLVED
            reportRepository.updateStatus(reportId, newStatus, reviewerId)
            log.info("Report {} {} by {}", reportId, newStatus, reviewerId)
        }
    }
}

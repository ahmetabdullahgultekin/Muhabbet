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
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class ModerationService(
    private val reportRepository: ReportRepository,
    private val blockRepository: BlockRepository
) : ReportUserUseCase, BlockUserUseCase, ReviewReportsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun reportUser(
        reporterId: UUID,
        reportedUserId: UUID?,
        reportedMessageId: UUID?,
        reportedConversationId: UUID?,
        reason: ReportReason,
        description: String?
    ): UserReport {
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
        return report
    }

    @Transactional
    override fun blockUser(blockerId: UUID, blockedId: UUID) {
        if (blockerId == blockedId) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR, "Kendinizi engelleyemezsiniz")
        }
        if (!blockRepository.exists(blockerId, blockedId)) {
            blockRepository.save(UserBlock(blockerId = blockerId, blockedId = blockedId))
            log.info("User {} blocked {}", blockerId, blockedId)
        }
    }

    @Transactional
    override fun unblockUser(blockerId: UUID, blockedId: UUID) {
        blockRepository.delete(blockerId, blockedId)
        log.info("User {} unblocked {}", blockerId, blockedId)
    }

    @Transactional(readOnly = true)
    override fun getBlockedUsers(userId: UUID): List<UUID> {
        return blockRepository.findByBlockerId(userId).map { it.blockedId }
    }

    @Transactional(readOnly = true)
    override fun isBlocked(blockerId: UUID, blockedId: UUID): Boolean {
        return blockRepository.exists(blockerId, blockedId)
    }

    @Transactional(readOnly = true)
    override fun getPendingReports(limit: Int, offset: Int): List<UserReport> {
        return reportRepository.findByStatus(ReportStatus.PENDING, limit.coerceIn(1, 100), offset.coerceAtLeast(0))
    }

    @Transactional
    override fun resolveReport(reportId: UUID, reviewerId: UUID, dismiss: Boolean) {
        val report = reportRepository.findById(reportId)
            ?: throw BusinessException(ErrorCode.VALIDATION_ERROR, "Rapor bulunamadı")
        val newStatus = if (dismiss) ReportStatus.DISMISSED else ReportStatus.RESOLVED
        reportRepository.updateStatus(reportId, newStatus, reviewerId)
        log.info("Report {} {} by {}", reportId, newStatus, reviewerId)
    }
}

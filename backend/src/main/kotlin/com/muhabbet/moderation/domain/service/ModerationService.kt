package com.muhabbet.moderation.domain.service

import com.muhabbet.moderation.domain.model.ReportReason
import com.muhabbet.moderation.domain.model.ReportReview
import com.muhabbet.moderation.domain.model.ReportStatus
import com.muhabbet.moderation.domain.model.ReportTarget
import com.muhabbet.moderation.domain.model.UserBlock
import com.muhabbet.moderation.domain.model.UserReport
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReportUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReviewReportsUseCase
import com.muhabbet.moderation.domain.port.out.BlockRepository
import com.muhabbet.moderation.domain.port.out.ReportEvidencePort
import com.muhabbet.moderation.domain.port.out.ReportRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

open class ModerationService(
    private val reportRepository: ReportRepository,
    private val blockRepository: BlockRepository,
    private val evidence: ReportEvidencePort,
    private val clock: Clock = Clock.systemUTC()
) : ReportUserUseCase, BlockUserUseCase, ReviewReportsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Files a report on exactly one target the reporter can see (#891). A message report captures
     * the message as it stands, so the sender deleting it afterwards does not delete the evidence.
     * Reporting the same target again while the first report is still PENDING returns that report
     * rather than queueing a duplicate for the reviewers.
     */
    @Transactional
    override fun reportUser(
        reporterId: UUID,
        reportedUserId: UUID?,
        reportedMessageId: UUID?,
        reportedConversationId: UUID?,
        reason: ReportReason,
        description: String?
    ): UserReport {
        val target = ReportTarget.of(reportedUserId, reportedMessageId, reportedConversationId)
            ?: throw BusinessException(ErrorCode.VALIDATION_ERROR)

        val snapshot = when (target) {
            is ReportTarget.User -> {
                if (target.userId == reporterId) throw BusinessException(ErrorCode.VALIDATION_ERROR)
                null
            }
            is ReportTarget.Message -> evidence.snapshotVisibleMessage(reporterId, target.messageId)
                ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
            is ReportTarget.Conversation -> {
                if (!evidence.isConversationMember(reporterId, target.conversationId)) {
                    throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
                }
                null
            }
        }

        reportRepository.findPendingByReporterAndTarget(reporterId, target)?.let { return it }

        val report = reportRepository.save(
            UserReport.open(reporterId, target, reason, description, snapshot, clock.instant())
        )
        log.info("Report created: id={}, reporter={}, reason={}", report.id, reporterId, reason)
        return report
    }

    /** Idempotent at the database: a double tap is two successes and one row, never a 500. */
    @Transactional
    override fun blockUser(blockerId: UUID, blockedId: UUID) {
        if (blockerId == blockedId) {
            throw BusinessException(ErrorCode.BLOCK_SELF)
        }
        if (blockRepository.insertIfAbsent(UserBlock(blockerId = blockerId, blockedId = blockedId, createdAt = clock.instant()))) {
            log.info("User {} blocked {}", blockerId, blockedId)
        }
    }

    @Transactional
    override fun unblockUser(blockerId: UUID, blockedId: UUID) {
        blockRepository.delete(blockerId, blockedId)
        log.info("User {} unblocked {}", blockerId, blockedId)
    }

    @Transactional(readOnly = true)
    override fun getBlockedUsers(userId: UUID): List<UserBlock> {
        return blockRepository.findByBlockerId(userId)
    }

    @Transactional(readOnly = true)
    override fun isBlocked(blockerId: UUID, blockedId: UUID): Boolean {
        return blockRepository.exists(blockerId, blockedId)
    }

    @Transactional(readOnly = true)
    override fun findBlockersAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> {
        return blockRepository.findBlockersAmong(userId, candidateIds)
    }

    @Transactional(readOnly = true)
    override fun findBlockedAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> {
        return blockRepository.findBlockedAmong(userId, candidateIds)
    }

    @Transactional(readOnly = true)
    override fun getPendingReports(limit: Int, offset: Int): List<UserReport> {
        return reportRepository.findByStatus(ReportStatus.PENDING, limit.coerceIn(1, 100), offset.coerceAtLeast(0))
    }

    @Transactional
    override fun resolveReport(reportId: UUID, reviewerId: UUID, dismiss: Boolean) {
        val report = reportRepository.findById(reportId)
            ?: throw BusinessException(ErrorCode.REPORT_NOT_FOUND)
        val now = clock.instant()
        val closed = when (val review = if (dismiss) report.dismiss(reviewerId, now) else report.resolve(reviewerId, now)) {
            is ReportReview.AlreadyClosed -> throw BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED)
            is ReportReview.Closed -> review.report
        }
        // Conditional on PENDING at the database too: two reviewers who both read it as PENDING
        // cannot both decide it.
        if (!reportRepository.close(closed)) {
            throw BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED)
        }
        log.info("Report {} {} by {}", reportId, closed.status, reviewerId)
    }
}

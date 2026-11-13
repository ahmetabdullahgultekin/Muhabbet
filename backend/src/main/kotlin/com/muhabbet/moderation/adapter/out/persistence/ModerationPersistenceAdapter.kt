package com.muhabbet.moderation.adapter.out.persistence

import com.muhabbet.moderation.domain.model.MessageEvidence
import com.muhabbet.moderation.domain.model.ReportStatus
import com.muhabbet.moderation.domain.model.ReportTarget
import com.muhabbet.moderation.domain.model.UserBlock
import com.muhabbet.moderation.domain.model.UserReport
import com.muhabbet.moderation.domain.port.out.BlockRepository
import com.muhabbet.moderation.domain.port.out.ReportRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ReportPersistenceAdapter(
    private val springDataReportRepository: SpringDataReportRepository
) : ReportRepository {

    override fun save(report: UserReport): UserReport {
        val evidence = report.evidence
        val entity = ReportJpaEntity(
            id = report.id,
            reporterId = report.reporterId,
            reportedUserId = report.reportedUserId,
            reportedMessageId = report.reportedMessageId,
            reportedConversationId = report.reportedConversationId,
            reason = report.reason,
            description = report.description,
            status = report.status,
            createdAt = report.createdAt,
            evidenceMessageId = evidence?.messageId,
            evidenceConversationId = evidence?.conversationId,
            evidenceSenderId = evidence?.senderId,
            evidenceContentType = evidence?.contentType,
            evidenceContent = evidence?.content,
            evidenceMediaUrl = evidence?.mediaUrl,
            evidenceSentAt = evidence?.sentAt
        )
        return springDataReportRepository.save(entity).toDomain()
    }

    override fun findById(id: UUID): UserReport? {
        return springDataReportRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByStatus(status: ReportStatus, limit: Int, offset: Int): List<UserReport> {
        return springDataReportRepository
            .findByStatusOrderByCreatedAtDesc(status, PageRequest.of(offset / limit, limit))
            .map { it.toDomain() }
    }

    override fun findPendingByReporterAndTarget(reporterId: UUID, target: ReportTarget): UserReport? {
        val pending = ReportStatus.PENDING
        val entity = when (target) {
            is ReportTarget.Message -> springDataReportRepository
                .findFirstByReporterIdAndReportedMessageIdAndStatus(reporterId, target.messageId, pending)
            is ReportTarget.Conversation -> springDataReportRepository
                .findFirstByReporterIdAndReportedConversationIdAndReportedMessageIdIsNullAndStatus(
                    reporterId, target.conversationId, pending
                )
            is ReportTarget.User -> springDataReportRepository
                .findFirstByReporterIdAndReportedUserIdAndReportedMessageIdIsNullAndReportedConversationIdIsNullAndStatus(
                    reporterId, target.userId, pending
                )
        }
        return entity?.toDomain()
    }

    override fun close(closed: UserReport): Boolean {
        val reviewer = closed.reviewedBy ?: return false
        val at = closed.resolvedAt ?: return false
        return springDataReportRepository.close(closed.id, closed.status.name, reviewer, at) == 1
    }
}

@Component
class BlockPersistenceAdapter(
    private val springDataBlockRepository: SpringDataBlockRepository
) : BlockRepository {

    override fun insertIfAbsent(block: UserBlock): Boolean =
        springDataBlockRepository.insertIfAbsent(block.id, block.blockerId, block.blockedId, block.createdAt) == 1

    override fun delete(blockerId: UUID, blockedId: UUID) {
        springDataBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId)
    }

    override fun findByBlockerId(blockerId: UUID): List<UserBlock> {
        return springDataBlockRepository.findByBlockerId(blockerId).map { it.toDomain() }
    }

    override fun exists(blockerId: UUID, blockedId: UUID): Boolean {
        return springDataBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)
    }

    override fun findBlockersAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> {
        // An empty IN () is both pointless and invalid on some engines — short-circuit before SQL.
        if (candidateIds.isEmpty()) return emptySet()
        return springDataBlockRepository
            .findByBlockedIdAndBlockerIdIn(userId, candidateIds.distinct())
            .mapTo(mutableSetOf()) { it.blockerId }
    }

    override fun findBlockedAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> {
        if (candidateIds.isEmpty()) return emptySet()
        return springDataBlockRepository
            .findByBlockerIdAndBlockedIdIn(userId, candidateIds.distinct())
            .mapTo(mutableSetOf()) { it.blockedId }
    }
}

private fun ReportJpaEntity.toDomain() = UserReport(
    id = id,
    reporterId = reporterId,
    reportedUserId = reportedUserId,
    reportedMessageId = reportedMessageId,
    reportedConversationId = reportedConversationId,
    reason = reason,
    description = description,
    status = status,
    reviewedBy = reviewedBy,
    resolvedAt = resolvedAt,
    createdAt = createdAt,
    evidence = toEvidence()
)

private fun ReportJpaEntity.toEvidence(): MessageEvidence? {
    val messageId = evidenceMessageId ?: return null
    return MessageEvidence(
        messageId = messageId,
        conversationId = evidenceConversationId ?: return null,
        senderId = evidenceSenderId ?: return null,
        contentType = evidenceContentType ?: return null,
        content = evidenceContent ?: "",
        mediaUrl = evidenceMediaUrl,
        sentAt = evidenceSentAt ?: return null
    )
}

private fun BlockJpaEntity.toDomain() = UserBlock(
    id = id,
    blockerId = blockerId,
    blockedId = blockedId,
    createdAt = createdAt
)

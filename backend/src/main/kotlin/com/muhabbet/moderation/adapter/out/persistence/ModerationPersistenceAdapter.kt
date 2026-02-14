package com.muhabbet.moderation.adapter.out.persistence

import com.muhabbet.moderation.domain.model.ReportReason
import com.muhabbet.moderation.domain.model.ReportStatus
import com.muhabbet.moderation.domain.model.UserBlock
import com.muhabbet.moderation.domain.model.UserReport
import com.muhabbet.moderation.domain.port.out.BlockRepository
import com.muhabbet.moderation.domain.port.out.ReportRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class ReportPersistenceAdapter(
    private val springDataReportRepository: SpringDataReportRepository
) : ReportRepository {

    override fun save(report: UserReport): UserReport {
        val entity = ReportJpaEntity(
            id = report.id,
            reporterId = report.reporterId,
            reportedUserId = report.reportedUserId,
            reportedMessageId = report.reportedMessageId,
            reportedConversationId = report.reportedConversationId,
            reason = report.reason.name,
            description = report.description,
            status = report.status.name,
            createdAt = report.createdAt
        )
        val saved = springDataReportRepository.save(entity)
        return saved.toDomain()
    }

    override fun findById(id: UUID): UserReport? {
        return springDataReportRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByStatus(status: ReportStatus, limit: Int, offset: Int): List<UserReport> {
        return springDataReportRepository
            .findByStatusOrderByCreatedAtDesc(status.name, PageRequest.of(offset / limit, limit))
            .map { it.toDomain() }
    }

    override fun updateStatus(id: UUID, status: ReportStatus, reviewerId: UUID) {
        springDataReportRepository.findById(id).ifPresent { entity ->
            entity.status = status.name
            entity.reviewedBy = reviewerId
            entity.resolvedAt = Instant.now()
            springDataReportRepository.save(entity)
        }
    }
}

@Component
class BlockPersistenceAdapter(
    private val springDataBlockRepository: SpringDataBlockRepository
) : BlockRepository {

    override fun save(block: UserBlock): UserBlock {
        val entity = BlockJpaEntity(
            id = block.id,
            blockerId = block.blockerId,
            blockedId = block.blockedId,
            createdAt = block.createdAt
        )
        val saved = springDataBlockRepository.save(entity)
        return saved.toDomain()
    }

    override fun delete(blockerId: UUID, blockedId: UUID) {
        springDataBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId)
    }

    override fun findByBlockerId(blockerId: UUID): List<UserBlock> {
        return springDataBlockRepository.findByBlockerId(blockerId).map { it.toDomain() }
    }

    override fun exists(blockerId: UUID, blockedId: UUID): Boolean {
        return springDataBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)
    }
}

private fun ReportJpaEntity.toDomain() = UserReport(
    id = id,
    reporterId = reporterId,
    reportedUserId = reportedUserId,
    reportedMessageId = reportedMessageId,
    reportedConversationId = reportedConversationId,
    reason = ReportReason.valueOf(reason),
    description = description,
    status = ReportStatus.valueOf(status),
    reviewedBy = reviewedBy,
    resolvedAt = resolvedAt,
    createdAt = createdAt
)

private fun BlockJpaEntity.toDomain() = UserBlock(
    id = id,
    blockerId = blockerId,
    blockedId = blockedId,
    createdAt = createdAt
)

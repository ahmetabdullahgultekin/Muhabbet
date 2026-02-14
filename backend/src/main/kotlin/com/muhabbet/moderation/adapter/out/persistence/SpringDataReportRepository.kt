package com.muhabbet.moderation.adapter.out.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataReportRepository : JpaRepository<ReportJpaEntity, UUID> {
    fun findByStatusOrderByCreatedAtDesc(status: String, pageable: Pageable): List<ReportJpaEntity>
}

interface SpringDataBlockRepository : JpaRepository<BlockJpaEntity, UUID> {
    fun findByBlockerId(blockerId: UUID): List<BlockJpaEntity>
    fun existsByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): Boolean
    fun deleteByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID)
}

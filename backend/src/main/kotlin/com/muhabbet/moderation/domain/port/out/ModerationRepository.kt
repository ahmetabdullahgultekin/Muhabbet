package com.muhabbet.moderation.domain.port.out

import com.muhabbet.moderation.domain.model.ReportStatus
import com.muhabbet.moderation.domain.model.UserBlock
import com.muhabbet.moderation.domain.model.UserReport
import java.util.UUID

interface ReportRepository {
    fun save(report: UserReport): UserReport
    fun findById(id: UUID): UserReport?
    fun findByStatus(status: ReportStatus, limit: Int, offset: Int): List<UserReport>
    fun updateStatus(id: UUID, status: ReportStatus, reviewerId: UUID)
}

interface BlockRepository {
    fun save(block: UserBlock): UserBlock
    fun delete(blockerId: UUID, blockedId: UUID)
    fun findByBlockerId(blockerId: UUID): List<UserBlock>
    fun exists(blockerId: UUID, blockedId: UUID): Boolean
}

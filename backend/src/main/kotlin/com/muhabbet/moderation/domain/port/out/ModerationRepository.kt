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
    /**
     * Records that [blockerId] blocks [blockedId], and reports whether this call created the row
     * (#899). Idempotent by the `UNIQUE(blocker_id, blocked_id)` constraint itself rather than by
     * a preceding existence check, which a double tap could pass twice and turn into a 500.
     */
    fun blockIfAbsent(blockerId: UUID, blockedId: UUID): Boolean
    fun delete(blockerId: UUID, blockedId: UUID)
    fun findByBlockerId(blockerId: UUID): List<UserBlock>
    fun exists(blockerId: UUID, blockedId: UUID): Boolean

    /** Which of [candidateIds] have blocked [userId]. Batched — presence asks about a whole page. */
    fun findBlockersAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID>

    /**
     * Which of [candidateIds] [userId] has blocked. The mirror of [findBlockersAmong] and batched
     * for the same reason — a feed asks it about every author on the screen at once.
     */
    fun findBlockedAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID>
}

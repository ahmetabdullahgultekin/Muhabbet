package com.muhabbet.moderation.domain.port.out

import com.muhabbet.moderation.domain.model.ReportStatus
import com.muhabbet.moderation.domain.model.ReportTarget
import com.muhabbet.moderation.domain.model.UserBlock
import com.muhabbet.moderation.domain.model.UserReport
import java.util.UUID

interface ReportRepository {
    fun save(report: UserReport): UserReport
    fun findById(id: UUID): UserReport?
    fun findByStatus(status: ReportStatus, limit: Int, offset: Int): List<UserReport>

    /** The reporter's still-PENDING report on [target], if any: a repeat report returns it instead of a copy. */
    fun findPendingByReporterAndTarget(reporterId: UUID, target: ReportTarget): UserReport?

    /**
     * Writes [closed]'s status, reviewer and time **only if the stored report is still PENDING**,
     * returning whether it did — so two reviewers deciding at once cannot overwrite each other.
     */
    fun close(closed: UserReport): Boolean
}

interface BlockRepository {
    /**
     * Records the block unless it already exists, returning whether a row was written. One
     * statement (`ON CONFLICT DO NOTHING`), so a double tap cannot race into the unique key (#891).
     */
    fun insertIfAbsent(block: UserBlock): Boolean
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

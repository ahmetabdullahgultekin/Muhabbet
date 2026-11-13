package com.muhabbet.moderation.adapter.out.persistence

import com.muhabbet.moderation.domain.model.ReportStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataReportRepository : JpaRepository<ReportJpaEntity, UUID> {
    fun findByStatusOrderByCreatedAtDesc(status: ReportStatus, pageable: Pageable): List<ReportJpaEntity>

    fun findFirstByReporterIdAndReportedMessageIdAndStatus(
        reporterId: UUID, reportedMessageId: UUID, status: ReportStatus
    ): ReportJpaEntity?

    fun findFirstByReporterIdAndReportedConversationIdAndReportedMessageIdIsNullAndStatus(
        reporterId: UUID, reportedConversationId: UUID, status: ReportStatus
    ): ReportJpaEntity?

    fun findFirstByReporterIdAndReportedUserIdAndReportedMessageIdIsNullAndReportedConversationIdIsNullAndStatus(
        reporterId: UUID, reportedUserId: UUID, status: ReportStatus
    ): ReportJpaEntity?

    /** Conditional on PENDING; 0 rows means the report was already decided. */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE user_reports
            SET status = :status, reviewed_by = :reviewedBy, resolved_at = :resolvedAt
            WHERE id = :id AND status = 'PENDING'
        """,
        nativeQuery = true
    )
    fun close(id: UUID, status: String, reviewedBy: UUID, resolvedAt: Instant): Int
}

interface SpringDataBlockRepository : JpaRepository<BlockJpaEntity, UUID> {
    fun findByBlockerId(blockerId: UUID): List<BlockJpaEntity>
    fun existsByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): Boolean
    fun deleteByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID)

    /**
     * The block, unless it is already there. Rides `UNIQUE(blocker_id, blocked_id)` from V15, so two
     * concurrent blocks of the same person produce one row and two successes instead of a
     * constraint violation surfacing as a 500 (#891).
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO user_blocks (id, blocker_id, blocked_id, created_at)
            VALUES (:id, :blockerId, :blockedId, :createdAt)
            ON CONFLICT (blocker_id, blocked_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(id: UUID, blockerId: UUID, blockedId: UUID, createdAt: Instant): Int

    /**
     * The reverse of [findByBlockerId] and narrowed to a candidate list: which of these people have
     * blocked me. Presence resolution asks this about a whole page of conversation participants at
     * once, so it must be one query rather than one per participant.
     */
    fun findByBlockedIdAndBlockerIdIn(blockedId: UUID, blockerIds: Collection<UUID>): List<BlockJpaEntity>

    /**
     * [findByBlockerId] narrowed to a candidate list: which of these people have I blocked. The
     * `UNIQUE(blocker_id, blocked_id)` on `user_blocks` is the index this rides, so no migration
     * comes with it.
     */
    fun findByBlockerIdAndBlockedIdIn(blockerId: UUID, blockedIds: Collection<UUID>): List<BlockJpaEntity>
}

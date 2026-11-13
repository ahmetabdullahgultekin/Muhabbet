package com.muhabbet.moderation.adapter.out.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataReportRepository : JpaRepository<ReportJpaEntity, UUID> {
    fun findByStatusOrderByCreatedAtDesc(status: String, pageable: Pageable): List<ReportJpaEntity>
}

interface SpringDataBlockRepository : JpaRepository<BlockJpaEntity, UUID> {
    /**
     * Inserts the block unless the pair already has one (#899). A concurrent identical insert makes
     * this wait for it and then insert nothing, instead of raising a unique violation.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        nativeQuery = true,
        value = """
        INSERT INTO user_blocks (id, blocker_id, blocked_id, created_at)
        VALUES (:id, :blockerId, :blockedId, :createdAt)
        ON CONFLICT (blocker_id, blocked_id) DO NOTHING
        """
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("blockerId") blockerId: UUID,
        @Param("blockedId") blockedId: UUID,
        @Param("createdAt") createdAt: Instant
    ): Int

    fun findByBlockerId(blockerId: UUID): List<BlockJpaEntity>
    fun existsByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID): Boolean
    fun deleteByBlockerIdAndBlockedId(blockerId: UUID, blockedId: UUID)

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

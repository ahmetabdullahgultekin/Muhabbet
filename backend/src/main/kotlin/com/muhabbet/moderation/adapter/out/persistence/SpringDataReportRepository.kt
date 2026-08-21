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

package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.StatusViewJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataStatusViewRepository : JpaRepository<StatusViewJpaEntity, UUID> {

    /**
     * One row per (status, viewer), whatever the race: two devices opening the same story at once,
     * or a client retrying after a dropped response, both land on the unique constraint and the
     * second becomes a no-op. A find-then-insert would let both through and fail the second with a
     * constraint violation.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        INSERT INTO status_views (id, status_id, viewer_id, viewed_at)
        VALUES (gen_random_uuid(), :statusId, :viewerId, CAST(:viewedAt AS TIMESTAMPTZ))
        ON CONFLICT (status_id, viewer_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("statusId") statusId: UUID,
        @Param("viewerId") viewerId: UUID,
        @Param("viewedAt") viewedAt: Instant
    ): Int

    @Query("SELECT v.statusId FROM StatusViewJpaEntity v WHERE v.viewerId = :viewerId AND v.statusId IN :statusIds")
    fun findViewedStatusIds(
        @Param("viewerId") viewerId: UUID,
        @Param("statusIds") statusIds: Collection<UUID>
    ): List<UUID>
}

package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceLinkSessionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataDeviceLinkSessionRepository : JpaRepository<DeviceLinkSessionJpaEntity, UUID> {
    fun findByTokenHash(tokenHash: String): DeviceLinkSessionJpaEntity?
    fun findByUserId(userId: UUID): List<DeviceLinkSessionJpaEntity>

    /**
     * The completion claim (#884): one conditional UPDATE, so the row lock serialises concurrent
     * completes and the loser re-reads a status that is no longer PENDING. Returns rows updated.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE device_link_sessions
            SET status = 'COMPLETED',
                companion_platform = :companionPlatform,
                companion_device_name = :companionDeviceName,
                public_bundle = :publicBundle,
                linked_device_id = :linkedDeviceId,
                completed_at = :completedAt
            WHERE id = :id
              AND status = 'PENDING'
              AND expires_at > :now
        """,
        nativeQuery = true
    )
    fun claimCompletion(
        id: UUID,
        companionPlatform: String?,
        companionDeviceName: String?,
        publicBundle: String?,
        linkedDeviceId: UUID,
        completedAt: Instant,
        now: Instant
    ): Int
}

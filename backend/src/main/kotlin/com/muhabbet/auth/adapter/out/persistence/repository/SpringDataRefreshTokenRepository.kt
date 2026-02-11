package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.RefreshTokenJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataRefreshTokenRepository : JpaRepository<RefreshTokenJpaEntity, UUID> {

    @Query(
        """
        SELECT r FROM RefreshTokenJpaEntity r
        WHERE r.tokenHash = :tokenHash
          AND r.revokedAt IS NULL
          AND r.expiresAt > :now
        """
    )
    fun findByTokenHashActive(tokenHash: String, now: Instant): RefreshTokenJpaEntity?

    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :now WHERE r.userId = :userId AND r.deviceId = :deviceId AND r.revokedAt IS NULL")
    fun revokeAllForDevice(userId: UUID, deviceId: UUID, now: Instant)

    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :now WHERE r.tokenHash = :tokenHash AND r.revokedAt IS NULL")
    fun revokeByTokenHash(tokenHash: String, now: Instant)
}

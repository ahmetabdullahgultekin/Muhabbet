package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceJpaEntity
import com.muhabbet.auth.domain.model.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** The live-session row the REST filter checks a token against (#923). */
interface LiveSessionRow {
    val tokenVersion: Int
    val admin: Boolean
}

interface SpringDataDeviceRepository : JpaRepository<DeviceJpaEntity, UUID> {
    fun findByUserId(userId: UUID): List<DeviceJpaEntity>
    fun findByUserIdIn(userIds: Collection<UUID>): List<DeviceJpaEntity>
    fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<DeviceJpaEntity>

    fun findByUserIdAndInstallationIdAndRevokedAtIsNull(userId: UUID, installationId: UUID): DeviceJpaEntity?

    /** See `DeviceRepository.findLegacyPrimaryCandidate`. */
    @Query(
        """
        SELECT d FROM DeviceJpaEntity d
        WHERE d.userId = :userId
          AND d.platform = :platform
          AND d.installationId IS NULL
          AND d.linkedByDeviceId IS NULL
          AND d.revokedAt IS NULL
        ORDER BY d.lastActiveAt DESC NULLS LAST, d.createdAt DESC
        LIMIT 1
        """
    )
    fun findLegacyPrimaryCandidate(
        @Param("userId") userId: UUID,
        @Param("platform") platform: String
    ): DeviceJpaEntity?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DeviceJpaEntity d SET d.tokenVersion = d.tokenVersion + 1 WHERE d.id = :deviceId")
    fun bumpTokenVersion(@Param("deviceId") deviceId: UUID): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DeviceJpaEntity d SET d.tokenVersion = d.tokenVersion + 1 WHERE d.userId = :userId")
    fun bumpTokenVersionForUser(@Param("userId") userId: UUID): Int

    /**
     * The device's current token epoch and its owner's admin flag — but only while the device is
     * not revoked and the account is ACTIVE and not deleted. One primary-key join per authenticated
     * request; null means "this session no longer exists".
     */
    @Query(
        """
        SELECT d.tokenVersion AS tokenVersion, u.isAdmin AS admin
        FROM DeviceJpaEntity d, UserJpaEntity u
        WHERE u.id = d.userId
          AND d.id = :deviceId
          AND d.userId = :userId
          AND d.revokedAt IS NULL
          AND u.status = :status
          AND u.deletedAt IS NULL
        """
    )
    fun findLiveSession(
        @Param("deviceId") deviceId: UUID,
        @Param("userId") userId: UUID,
        @Param("status") status: UserStatus
    ): LiveSessionRow?
}

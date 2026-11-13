package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataDeviceRepository : JpaRepository<DeviceJpaEntity, UUID> {
    fun findByUserIdAndPlatform(userId: UUID, platform: String): DeviceJpaEntity?
    fun findByUserId(userId: UUID): List<DeviceJpaEntity>
    fun findByUserIdIn(userIds: Collection<UUID>): List<DeviceJpaEntity>
    fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<DeviceJpaEntity>

    /** Sign-in's write: name and last-active only, never `push_token` (#908, see the port). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE DeviceJpaEntity d SET d.deviceName = :deviceName, d.lastActiveAt = :at WHERE d.id = :deviceId")
    fun recordSignIn(
        @Param("deviceId") deviceId: UUID,
        @Param("deviceName") deviceName: String,
        @Param("at") at: Instant
    ): Int

    /**
     * A transaction-scoped advisory lock keyed on the token (#908), released at commit or rollback.
     *
     * Without it, two transactions registering one token to two devices both find nobody to detach
     * — neither can see the other's uncommitted row — and the second fails on the unique index
     * instead of winning. `pg_advisory_xact_lock` returns void, so it is called from a FROM clause
     * and the statement selects a constant.
     */
    @Query(
        value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:pushToken, 0))",
        nativeQuery = true
    )
    fun lockPushToken(@Param("pushToken") pushToken: String): Int

    @Modifying(flushAutomatically = true)
    @Query(
        "UPDATE DeviceJpaEntity d SET d.pushToken = NULL " +
            "WHERE d.pushToken = :pushToken AND d.id <> :keepDeviceId"
    )
    fun detachPushTokenFromOthers(
        @Param("pushToken") pushToken: String,
        @Param("keepDeviceId") keepDeviceId: UUID
    ): Int

    @Modifying(flushAutomatically = true)
    @Query("UPDATE DeviceJpaEntity d SET d.pushToken = NULL WHERE d.pushToken = :pushToken")
    fun clearPushToken(@Param("pushToken") pushToken: String): Int
}

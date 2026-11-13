package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.RefreshTokenJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface SpringDataRefreshTokenRepository : JpaRepository<RefreshTokenJpaEntity, UUID> {

    fun findByUserId(userId: UUID): List<RefreshTokenJpaEntity>

    fun findByTokenHash(tokenHash: String): RefreshTokenJpaEntity?

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :now WHERE r.userId = :userId AND r.deviceId = :deviceId AND r.revokedAt IS NULL")
    fun revokeAllForDevice(userId: UUID, deviceId: UUID, now: Instant)

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :now WHERE r.userId = :userId AND r.revokedAt IS NULL")
    fun revokeAllForUser(userId: UUID, now: Instant)

    /**
     * The rotation itself (#880). `revokedAt IS NULL` in the WHERE clause is the whole point: the
     * second of two concurrent refreshes blocks on the row lock, re-evaluates the predicate after
     * the first commits, matches nothing and returns 0 — where the old `Unit`-returning variant let
     * it go on and mint a second chain.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE RefreshTokenJpaEntity r
           SET r.revokedAt = :now, r.rotatedAt = :now
         WHERE r.tokenHash = :tokenHash
           AND r.revokedAt IS NULL
           AND r.expiresAt > :now
        """
    )
    fun markRotated(@Param("tokenHash") tokenHash: String, @Param("now") now: Instant): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :now WHERE r.familyId = :familyId AND r.revokedAt IS NULL")
    fun revokeFamily(@Param("familyId") familyId: UUID, @Param("now") now: Instant): Int
}

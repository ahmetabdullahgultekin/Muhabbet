package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.OtpRequestJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataOtpRepository : JpaRepository<OtpRequestJpaEntity, UUID> {

    @Query(
        """
        SELECT o FROM OtpRequestJpaEntity o
        WHERE o.phoneNumber = :phoneNumber
          AND o.verified = false
          AND o.expiresAt > :now
        ORDER BY o.createdAt DESC
        LIMIT 1
        """
    )
    fun findActiveByPhoneNumber(phoneNumber: String, now: Instant): OtpRequestJpaEntity?

    /**
     * Claims one attempt, and refuses once [maxAttempts] have been spent. Returns rows updated: 1 when
     * the attempt was granted, 0 when the budget is already gone.
     *
     * The limit lives in the `WHERE` clause rather than in a preceding read, because checking and then
     * incrementing is three steps and concurrent verifies interleave inside them: every request reads
     * the same count, every request finds it under the limit, and the effective limit becomes the
     * attacker's concurrency rather than the configured number. A single conditional statement is
     * decided by the row lock.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE OtpRequestJpaEntity o
           SET o.attempts = o.attempts + 1
         WHERE o.id = :id
           AND o.attempts < :maxAttempts
        """
    )
    fun claimAttempt(@Param("id") id: UUID, @Param("maxAttempts") maxAttempts: Int): Int

    /**
     * Undoes one [claimAttempt] for a code that was found correct — see `OtpRepository.refundAttempt`.
     *
     * `attempts > 0` is a floor, not an optimisation: without it a stray refund would drive the
     * counter negative and quietly hand out extra guesses.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE OtpRequestJpaEntity o
           SET o.attempts = o.attempts - 1
         WHERE o.id = :id
           AND o.attempts > 0
        """
    )
    fun refundAttempt(@Param("id") id: UUID): Int

    /**
     * Marks verified with an UPDATE rather than by mutating a managed entity.
     *
     * The caller's persistence context is holding this row from the lookup that started `verifyOtp`,
     * with the attempt count as it was *before* [claimAttempt] committed in its own transaction. Loading
     * and saving it here would flush that stale value back over the increment on commit.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OtpRequestJpaEntity o SET o.verified = true WHERE o.id = :id")
    fun markVerified(@Param("id") id: UUID): Int
}

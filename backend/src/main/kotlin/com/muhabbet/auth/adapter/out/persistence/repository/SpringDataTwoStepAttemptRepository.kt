package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.TwoStepAttemptJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataTwoStepAttemptRepository : JpaRepository<TwoStepAttemptJpaEntity, UUID> {

    /**
     * Makes sure the counter row exists, without racing another request that is doing the same.
     *
     * Native because `ON CONFLICT` has no JPQL equivalent, and a read-then-insert would throw a
     * duplicate-key error on the second of two concurrent first guesses — which the caller would
     * then have to distinguish from a real failure.
     *
     * The `CAST(... AS TIMESTAMPTZ)` on every `Instant` parameter here and below is not decoration:
     * a native query gives Hibernate no mapping to infer from, so it binds an `Instant` as `text`
     * and Postgres refuses to compare or assign it to a `timestamptz` column.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        INSERT INTO two_step_attempts (user_id, failed_attempts, locked_until, updated_at)
        VALUES (:userId, 0, NULL, CAST(:now AS TIMESTAMPTZ))
        ON CONFLICT (user_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun ensureRow(@Param("userId") userId: UUID, @Param("now") now: Instant): Int

    /**
     * Claims one guess and, in the same statement, applies the lock when the budget runs out.
     * Returns rows updated: 1 when the guess was granted, 0 when the account is locked right now.
     *
     * One statement rather than read-check-write, for the reason on `SpringDataOtpRepository`: three
     * steps interleave, every concurrent guess reads the same under-limit count, and the effective
     * limit becomes the attacker's concurrency. The row lock decides it here instead.
     *
     * The `WHERE` admits an absent lock or an expired one. Inside the `SET`, therefore, a non-null
     * `locked_until` can only mean *expired*, so that branch starts a fresh window: count back to 1,
     * lock cleared. Otherwise the count rises and, on reaching [maxAttempts], the lock is stamped —
     * which is what stops the very next request rather than letting it spend another guess first.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        UPDATE two_step_attempts
           SET failed_attempts = CASE WHEN locked_until IS NOT NULL THEN 1
                                      ELSE failed_attempts + 1 END,
               locked_until = CASE WHEN locked_until IS NOT NULL THEN NULL
                                   WHEN failed_attempts + 1 >= :maxAttempts THEN CAST(:lockUntil AS TIMESTAMPTZ)
                                   ELSE NULL END,
               updated_at = CAST(:now AS TIMESTAMPTZ)
         WHERE user_id = :userId
           AND (locked_until IS NULL OR locked_until <= CAST(:now AS TIMESTAMPTZ))
        """,
        nativeQuery = true
    )
    fun claimAttempt(
        @Param("userId") userId: UUID,
        @Param("maxAttempts") maxAttempts: Int,
        @Param("now") now: Instant,
        @Param("lockUntil") lockUntil: Instant
    ): Int

    /** Clears the window. An UPDATE rather than a delete, so a row read concurrently still exists. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        UPDATE two_step_attempts
           SET failed_attempts = 0, locked_until = NULL, updated_at = CAST(:now AS TIMESTAMPTZ)
         WHERE user_id = :userId
        """,
        nativeQuery = true
    )
    fun clear(@Param("userId") userId: UUID, @Param("now") now: Instant): Int
}

package com.muhabbet.auth.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One row per user, holding how many two-step PIN guesses have failed and until when the account is
 * locked out (V27, #566).
 *
 * There is no domain model behind it and no mapper, on purpose. The whole lifecycle is the two
 * statements on `SpringDataTwoStepAttemptRepository`; nothing reads a field, so nothing can
 * accidentally write one back. That is also why the counters are not columns on `users`:
 * `UserJpaEntity.fromDomain` rebuilds the row from the domain model on every profile save, which
 * would reset the counter as a side effect of the attacker editing their own display name.
 */
@Entity
@Table(name = "two_step_attempts")
class TwoStepAttemptJpaEntity(
    @Id
    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "failed_attempts", nullable = false)
    var failedAttempts: Int = 0,

    /** Null means not locked. A value in the past is an expired lock the next claim clears. */
    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

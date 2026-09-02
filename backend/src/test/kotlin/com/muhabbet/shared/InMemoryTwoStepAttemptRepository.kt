package com.muhabbet.shared

import com.muhabbet.auth.domain.port.out.TwoStepAttemptRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The PIN attempt budget without a database, for the service-level tests.
 *
 * A relaxed mock would answer `false` — "locked out" — for every claim, so every two-step test would
 * fail on the lockout rather than on what it is testing, and a mock stubbed to always return `true`
 * would make the lockout untestable at this level. This behaves like the real adapter: it counts,
 * it locks, and an expired lock reopens the window. The atomicity the production query exists for is
 * the one thing it cannot model, which is why `TwoStepPinGateIntegrationTest` drives the real one.
 */
class InMemoryTwoStepAttemptRepository(
    private val clock: () -> Instant = Instant::now
) : TwoStepAttemptRepository {

    private data class Window(val failures: Int, val lockedUntil: Instant?)

    private val windows = mutableMapOf<UUID, Window>()

    /** Every claim made, granted or not — so a test can assert the counter was consulted at all. */
    var claims: Int = 0
        private set

    override fun claimAttempt(userId: UUID, maxAttempts: Int, lockFor: Duration): Boolean {
        claims++
        val now = clock()
        val current = windows[userId] ?: Window(0, null)
        val lockedUntil = current.lockedUntil
        if (lockedUntil != null && lockedUntil.isAfter(now)) return false

        // An expired lock starts a fresh window, exactly as the SQL CASE does.
        val failures = if (lockedUntil != null) 1 else current.failures + 1
        windows[userId] = Window(
            failures = failures,
            lockedUntil = if (lockedUntil == null && failures >= maxAttempts) now.plus(lockFor) else null
        )
        return true
    }

    override fun clear(userId: UUID) {
        windows.remove(userId)
    }

    /** True when the next claim would be refused. Reads the state without spending a guess. */
    fun isLocked(userId: UUID): Boolean =
        windows[userId]?.lockedUntil?.isAfter(clock()) == true
}

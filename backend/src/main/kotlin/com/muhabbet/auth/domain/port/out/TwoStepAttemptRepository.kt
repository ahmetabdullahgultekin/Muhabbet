package com.muhabbet.auth.domain.port.out

import java.time.Duration
import java.util.UUID

/**
 * The attempt budget for the two-step PIN (#566).
 *
 * A PIN is six digits — a million possibilities — and BCrypt only makes an *offline* sweep of a
 * stolen hash expensive. Online, the number of guesses is the whole defence, so it is counted
 * server-side and outside the caller's transaction.
 *
 * Modelled on [OtpRepository.claimAttempt] rather than on the Redis quota: this must **fail
 * closed**, and the OTP limiter's home — the database the sign-in already cannot proceed without —
 * gives that for free. Redis is allowed to fail open because losing it would otherwise mean nobody
 * can log in; a second factor that stops being enforced when a cache is unreachable is the failure
 * this issue is about.
 */
interface TwoStepAttemptRepository {

    /**
     * Claims one PIN guess for [userId], and reports whether it was granted.
     *
     * False means the account is currently locked out — not that the PIN was wrong. The caller must
     * refuse *before* comparing anything, so a locked account leaks nothing about the PIN.
     *
     * Counting and enforcing are one statement for the reason spelled out on
     * [OtpRepository.claimAttempt]: a read followed by an increment lets concurrent guesses
     * interleave, and the effective limit becomes the attacker's thread count.
     *
     * @param lockFor how long the lockout lasts once [maxAttempts] consecutive guesses have failed.
     *   A window and not a permanent block: a forgotten PIN typed three times too many must not
     *   end the account, and the guess rate a window permits — [maxAttempts] per window — is orders
     *   of magnitude below what brute force needs.
     */
    fun claimAttempt(userId: UUID, maxAttempts: Int, lockFor: Duration): Boolean

    /** Forgets the failures for [userId]. Called when a PIN is accepted, and when one is set. */
    fun clear(userId: UUID)
}

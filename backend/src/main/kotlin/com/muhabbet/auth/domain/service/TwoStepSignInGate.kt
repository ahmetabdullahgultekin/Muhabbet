package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.PasswordHasher
import com.muhabbet.auth.domain.port.out.TwoStepAttemptRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * The second factor at sign-in (#566): past [require] the caller has proved both factors, or has
 * not got past it.
 *
 * Both sign-in paths — OTP and Firebase — apply it, which is why it is a policy of its own rather
 * than a private method of one of them (#894). It sits in the entry points rather than in
 * [PhoneSignIn] because only they know which factor they have just checked, and putting it in the
 * shared tail would also gate the new-user branch, where there is no PIN to know.
 */
class TwoStepSignInGate(
    private val passwordHasher: PasswordHasher,
    /**
     * The guess budget for the second factor. Required rather than defaulted: a no-op default
     * would leave a six-digit PIN with no ceiling, and the whole point of the gate is that guessing
     * it must be expensive.
     */
    private val twoStepAttemptRepository: TwoStepAttemptRepository,
    private val maxAttempts: Int = 5,
    private val lockSeconds: Long = 900
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Two-step is on only when both halves are present; either alone is a half-written row. */
    fun isEnabledFor(user: User): Boolean = user.twoStepEnabledAt != null && user.twoStepPinHash != null

    /**
     * Order matters. The lockout is claimed **before** the PIN is compared, so a locked account
     * answers `AUTH_2FA_LOCKED` without the hash being consulted at all — otherwise the lock would
     * be a message rather than a control, and a client that ignored it would keep guessing.
     *
     * A missing PIN is not a failed guess and costs nothing: the client cannot know an account has a
     * second factor until it is told, and charging it for asking would mean every sign-in started
     * one attempt down.
     */
    fun require(user: User, pin: String?) {
        rejection(user, pin)?.let { throw BusinessException(it) }
        // A correct PIN ends the window, so five typos spread over a year never add up to a lockout.
        twoStepAttemptRepository.clear(user.id)
    }

    /** Why this sign-in may not proceed, or null when it may: a series of guards with one exit. */
    private fun rejection(user: User, pin: String?): ErrorCode? {
        val storedHash = user.twoStepPinHash ?: return ErrorCode.AUTH_2FA_NOT_ENABLED
        if (pin == null) {
            log.info("Two-step PIN required: userId={}", user.id)
            return ErrorCode.AUTH_2FA_PIN_REQUIRED
        }
        val granted = twoStepAttemptRepository.claimAttempt(
            userId = user.id,
            maxAttempts = maxAttempts,
            lockFor = Duration.ofSeconds(lockSeconds)
        )
        if (!granted) {
            log.warn("Two-step PIN locked out: userId={}", user.id)
            return ErrorCode.AUTH_2FA_LOCKED
        }
        if (!passwordHasher.matches(pin, storedHash)) {
            log.warn("Two-step PIN rejected: userId={}", user.id)
            return ErrorCode.AUTH_2FA_PIN_INVALID
        }
        return null
    }
}

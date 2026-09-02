package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.TwoStepStatus
import com.muhabbet.auth.domain.port.`in`.TwoStepVerificationUseCase
import com.muhabbet.auth.domain.port.out.TwoStepAttemptRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

open class TwoStepVerificationService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    /**
     * The same budget the sign-in gate uses (#566), shared on purpose.
     *
     * `/verify` answers whether a PIN is right and `/disable` turns the second factor off — both are
     * PIN oracles for anyone holding a stolen access token, and an unmetered oracle would make the
     * sign-in limiter pointless: guess here until it lands, then use it there. One counter per user
     * means guesses spent at either address come out of the same five.
     */
    private val twoStepAttemptRepository: TwoStepAttemptRepository,
    private val maxAttempts: Int = 5,
    private val lockSeconds: Long = 900
) : TwoStepVerificationUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    override fun status(userId: UUID): TwoStepStatus {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        return TwoStepStatus(
            enabled = user.twoStepEnabledAt != null && user.twoStepPinHash != null,
            hasRecoveryEmail = user.twoStepEmail != null
        )
    }

    @Transactional
    override fun setupPin(userId: UUID, pin: String, email: String?) {
        // The server is the authority on what a PIN is. Until now only the Compose screen checked,
        // so anything that reached this endpoint by another route — a script, an older build, a
        // future web client — could store an empty string as a second factor (#544).
        if (!ValidationRules.isValidTwoStepPin(pin)) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }

        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        if (user.twoStepEnabledAt != null) {
            throw BusinessException(ErrorCode.AUTH_2FA_ALREADY_ENABLED)
        }

        val hashedPin = passwordEncoder.encode(pin)
        val updated = user.copy(
            twoStepPinHash = hashedPin,
            twoStepEmail = email,
            twoStepEnabledAt = Instant.now(),
            updatedAt = Instant.now()
        )
        userRepository.save(updated)
        // A new PIN starts a new window. Otherwise a user who locked themselves out, signed in on a
        // device that was already trusted and set a fresh PIN would still be locked out of the next
        // sign-in by failures that no longer refer to anything.
        twoStepAttemptRepository.clear(userId)
        log.info("2FA enabled for user={}", userId)
    }

    @Transactional(readOnly = true)
    override fun verifyPin(userId: UUID, pin: String): Boolean {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val storedHash = user.twoStepPinHash
        if (user.twoStepEnabledAt == null || storedHash == null) {
            throw BusinessException(ErrorCode.AUTH_2FA_NOT_ENABLED)
        }

        claimAttempt(userId)
        val matches = passwordEncoder.matches(pin, storedHash)
        if (matches) twoStepAttemptRepository.clear(userId)
        return matches
    }

    @Transactional
    override fun disablePin(userId: UUID, currentPin: String) {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val storedHash = user.twoStepPinHash
        if (user.twoStepEnabledAt == null || storedHash == null) {
            throw BusinessException(ErrorCode.AUTH_2FA_NOT_ENABLED)
        }

        claimAttempt(userId)
        if (!passwordEncoder.matches(currentPin, storedHash)) {
            throw BusinessException(ErrorCode.AUTH_2FA_PIN_INVALID)
        }

        val updated = user.copy(
            twoStepPinHash = null,
            twoStepEmail = null,
            twoStepEnabledAt = null,
            updatedAt = Instant.now()
        )
        userRepository.save(updated)
        twoStepAttemptRepository.clear(userId)
        log.info("2FA disabled for user={}", userId)
    }

    /**
     * Claims one guess, or refuses because the account is locked out.
     *
     * Claimed before the hash is consulted, so a locked account learns nothing about the PIN — and
     * `REQUIRES_NEW` inside the adapter is what keeps the increment alive when the caller throws.
     */
    private fun claimAttempt(userId: UUID) {
        val granted = twoStepAttemptRepository.claimAttempt(
            userId = userId,
            maxAttempts = maxAttempts,
            lockFor = Duration.ofSeconds(lockSeconds)
        )
        if (!granted) {
            log.warn("2FA PIN locked out for user={}", userId)
            throw BusinessException(ErrorCode.AUTH_2FA_LOCKED)
        }
    }
}

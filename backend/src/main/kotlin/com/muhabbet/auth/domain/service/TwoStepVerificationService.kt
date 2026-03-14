package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.port.`in`.TwoStepVerificationUseCase
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class TwoStepVerificationService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : TwoStepVerificationUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun setupPin(userId: UUID, pin: String, email: String?) {
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
        log.info("2FA enabled for user={}", userId)
    }

    @Transactional(readOnly = true)
    override fun verifyPin(userId: UUID, pin: String): Boolean {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        if (user.twoStepEnabledAt == null || user.twoStepPinHash == null) {
            throw BusinessException(ErrorCode.AUTH_2FA_NOT_ENABLED)
        }

        return passwordEncoder.matches(pin, user.twoStepPinHash)
    }

    @Transactional
    override fun disablePin(userId: UUID, currentPin: String) {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        if (user.twoStepEnabledAt == null || user.twoStepPinHash == null) {
            throw BusinessException(ErrorCode.AUTH_2FA_NOT_ENABLED)
        }

        if (!passwordEncoder.matches(currentPin, user.twoStepPinHash)) {
            throw BusinessException(ErrorCode.AUTH_2FA_PIN_INVALID)
        }

        val updated = user.copy(
            twoStepPinHash = null,
            twoStepEmail = null,
            twoStepEnabledAt = null,
            updatedAt = Instant.now()
        )
        userRepository.save(updated)
        log.info("2FA disabled for user={}", userId)
    }

    @Transactional
    override fun resetPinViaEmail(userId: UUID, email: String) {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        if (user.twoStepEnabledAt == null) {
            throw BusinessException(ErrorCode.AUTH_2FA_NOT_ENABLED)
        }

        if (user.twoStepEmail == null || user.twoStepEmail != email) {
            throw BusinessException(ErrorCode.AUTH_2FA_PIN_INVALID)
        }

        // Reset the PIN — user must set up a new one
        val updated = user.copy(
            twoStepPinHash = null,
            twoStepEnabledAt = null,
            updatedAt = Instant.now()
        )
        userRepository.save(updated)
        log.info("2FA PIN reset via email for user={}", userId)
    }
}

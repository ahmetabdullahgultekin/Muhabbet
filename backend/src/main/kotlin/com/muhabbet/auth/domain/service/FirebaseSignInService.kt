package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.port.`in`.AuthResult
import com.muhabbet.auth.domain.port.`in`.FirebaseVerifyUseCase
import com.muhabbet.auth.domain.port.out.FirebaseTokenVerifier
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory

/**
 * Signing in with a Firebase phone-auth ID token: the second way a phone number is proved (#894).
 * Applies the same two-step gate as the OTP path and shares its sign-in tail.
 */
class FirebaseSignInService(
    /**
     * Verifies the Firebase ID token. Behind a port so the two-step gate on this path can be
     * exercised at all — see [FirebaseTokenVerifier].
     */
    private val firebaseTokenVerifier: FirebaseTokenVerifier,
    private val userRepository: UserRepository,
    private val twoStepGate: TwoStepSignInGate,
    private val phoneSignIn: PhoneSignIn,
    private val transactions: TransactionRunner
) : FirebaseVerifyUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun verifyFirebaseToken(
        idToken: String,
        deviceName: String,
        platform: String,
        twoStepPin: String?
    ): AuthResult = transactions.inTransaction {
        val phoneNumber = try {
            firebaseTokenVerifier.phoneNumberOf(idToken)
        } catch (e: IllegalArgumentException) {
            log.warn("Firebase token verification failed: {}", e.message)
            throw BusinessException(ErrorCode.AUTH_TOKEN_INVALID)
        } ?: throw BusinessException(ErrorCode.AUTH_INVALID_PHONE)

        if (!ValidationRules.isValidTurkishPhone(phoneNumber)) {
            throw BusinessException(ErrorCode.AUTH_INVALID_PHONE)
        }

        // Same gate as the OTP path, and it has to be here rather than in the shared tail: only the
        // two entry points know which factor they have just checked, and putting it in the tail
        // would also gate the new-user creation branch, where there is no PIN to know.
        val existing = userRepository.findByPhoneNumber(phoneNumber)
        if (existing != null && twoStepGate.isEnabledFor(existing)) {
            twoStepGate.require(existing, twoStepPin)
        }

        phoneSignIn.complete(phoneNumber, deviceName, platform, "Firebase verified", existing)
    }
}

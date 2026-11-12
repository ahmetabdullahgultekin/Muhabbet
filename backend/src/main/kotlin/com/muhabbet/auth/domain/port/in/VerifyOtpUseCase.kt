package com.muhabbet.auth.domain.port.`in`

import java.time.LocalDate

interface VerifyOtpUseCase {
    /**
     * Exchanges a verification code for tokens — unless the account has two-step verification on,
     * in which case [twoStepPin] must carry the PIN as well (#566).
     *
     * The PIN travels with the code rather than through a second endpoint holding a challenge token,
     * because a challenge is state that has to be issued, stored, expired and revoked, and every one
     * of those is another way for the gate to be got around. Here there is nothing to steal: a
     * caller who cannot present both factors in one request gets `AUTH_2FA_PIN_REQUIRED` and no
     * tokens.
     *
     * @param twoStepPin null on the first attempt, since the client cannot know whether the account
     *   has a second factor until it is told. A correct code with a missing PIN does **not** spend
     *   the code — see `OtpRepository.refundAttempt`.
     * @param birthDate required when the number has no account yet (#751): a new account without one
     *   is refused with `AUTH_AGE_REQUIRED` and the code is not spent, and one below the minimum age
     *   with `AUTH_AGE_BELOW_MINIMUM`. Ignored for an existing account, which declares it through
     *   [DeclareBirthDateUseCase] instead.
     */
    fun verifyOtp(
        phoneNumber: String,
        otp: String,
        deviceName: String,
        platform: String,
        twoStepPin: String? = null,
        birthDate: LocalDate? = null
    ): AuthResult
}

data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val userId: String,
    val deviceId: String,
    val isNewUser: Boolean,
    /** The account has no assured age yet (#751); the client asks for a date of birth before going on. */
    val ageVerificationRequired: Boolean = false
)

package com.muhabbet.auth.domain.port.`in`

import java.time.LocalDate

interface FirebaseVerifyUseCase {
    /**
     * The other way to mint tokens, and so the other half of the two-step gate (#566).
     *
     * Gating only the OTP path would have been worse than gating neither: the feature would look
     * enforced while the app's *default* sign-in — Firebase phone auth, with the backend OTP only as
     * a fallback — walked straight past it.
     *
     * The age gate (#751) is on both paths for the same reason: see [VerifyOtpUseCase.verifyOtp]'s
     * `birthDate`.
     */
    fun verifyFirebaseToken(
        idToken: String,
        deviceName: String,
        platform: String,
        twoStepPin: String? = null,
        birthDate: LocalDate? = null
    ): AuthResult
}

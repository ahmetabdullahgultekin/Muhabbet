package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * Locale-independent classification of a Firebase phone-auth failure.
 *
 * Carried on [PhoneVerificationResult.Error] so the UI can decide whether to fall back to the
 * backend OTP flow WITHOUT substring-matching localized message text (which false-negatives on
 * Turkish-locale devices — our entire user base). Mapped from `FirebaseAuthException.errorCode`
 * on Android.
 */
enum class PhoneAuthErrorCode {
    /** Rate limiting / abuse protection (e.g. ERROR_TOO_MANY_REQUESTS, ERROR_QUOTA_EXCEEDED). */
    RATE_LIMITED,

    /** Firebase mis/under-configuration (bad/missing API key, app not authorized, internal error). */
    CONFIGURATION,

    /** The phone number was rejected as invalid by Firebase. */
    INVALID_PHONE,

    /** Anything else / could not be classified — caller may inspect [PhoneVerificationResult.Error.message]. */
    UNKNOWN
}

sealed class PhoneVerificationResult {
    data class CodeSent(val verificationId: String) : PhoneVerificationResult()
    data class AutoVerified(val idToken: String) : PhoneVerificationResult()
    data class Error(
        val message: String,
        val code: PhoneAuthErrorCode = PhoneAuthErrorCode.UNKNOWN
    ) : PhoneVerificationResult()
}

interface FirebasePhoneAuth {
    fun isAvailable(): Boolean
    suspend fun startVerification(phoneNumber: String): PhoneVerificationResult
    suspend fun verifyCode(verificationId: String, code: String): String
}

@Composable
expect fun rememberFirebasePhoneAuth(): FirebasePhoneAuth?

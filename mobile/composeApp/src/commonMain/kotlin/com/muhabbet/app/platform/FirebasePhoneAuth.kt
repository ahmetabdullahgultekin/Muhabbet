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

    /**
     * The SDK never answered the verification request (#860). Treated like a configuration failure
     * by the login screen: the backend OTP is offered instead of an endless spinner.
     */
    TIMEOUT,

    /**
     * A credential was obtained but signing in with it failed, or yielded no ID token (#860). Used
     * to hang the login screen on the auto-verification path, or to hand the backend an empty token.
     */
    SIGN_IN_FAILED,

    /** Phone auth is not available on this platform or build at all (iOS today). */
    UNAVAILABLE,

    /** Anything else / could not be classified. */
    UNKNOWN
}

/**
 * Outcome of starting phone verification. An error carries only a [PhoneAuthErrorCode], never a
 * message: the SDK's text is English or device-localized and must not reach the UI (#860), which
 * maps the code to its own string resource.
 */
sealed class PhoneVerificationResult {
    data class CodeSent(val verificationId: String) : PhoneVerificationResult()
    data class AutoVerified(val idToken: String) : PhoneVerificationResult()
    data class Error(val code: PhoneAuthErrorCode) : PhoneVerificationResult()
}

/** Thrown by [FirebasePhoneAuth.verifyCode]; carries a code, not a user-visible message. */
class PhoneAuthException(val code: PhoneAuthErrorCode) : Exception(code.name)

interface FirebasePhoneAuth {
    /** True only when the SDK can actually run here — not a constant (#860). */
    fun isAvailable(): Boolean

    /**
     * Starts verification. Calling it again for the same number after a `CodeSent` is a resend: the
     * SDK's resend token from the first call is reused rather than starting from scratch (#860).
     */
    suspend fun startVerification(phoneNumber: String): PhoneVerificationResult

    /** Returns a non-blank ID token or throws [PhoneAuthException]. */
    suspend fun verifyCode(verificationId: String, code: String): String
}

@Composable
expect fun rememberFirebasePhoneAuth(): FirebasePhoneAuth?

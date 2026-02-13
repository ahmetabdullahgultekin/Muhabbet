package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS Firebase Phone Auth implementation.
 *
 * Firebase iOS SDK must be integrated via CocoaPods/SPM for this to work in production.
 * For MVP: Returns a stub that reports unavailability, allowing the app to fall back
 * to backend-only OTP verification (which works without Firebase on iOS).
 */
@Composable
actual fun rememberFirebasePhoneAuth(): FirebasePhoneAuth? {
    return remember { IosFirebasePhoneAuth() }
}

private class IosFirebasePhoneAuth : FirebasePhoneAuth {

    override fun isAvailable(): Boolean {
        // Firebase iOS SDK not yet integrated via CocoaPods
        // App falls back to backend-only OTP flow when this returns false
        return false
    }

    override suspend fun startVerification(phoneNumber: String): PhoneVerificationResult {
        return PhoneVerificationResult.Error("Firebase iOS SDK not configured")
    }

    override suspend fun verifyCode(verificationId: String, code: String): String {
        throw UnsupportedOperationException("Firebase iOS SDK not configured")
    }
}

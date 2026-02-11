package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

sealed class PhoneVerificationResult {
    data class CodeSent(val verificationId: String) : PhoneVerificationResult()
    data class AutoVerified(val idToken: String) : PhoneVerificationResult()
    data class Error(val message: String) : PhoneVerificationResult()
}

interface FirebasePhoneAuth {
    fun isAvailable(): Boolean
    suspend fun startVerification(phoneNumber: String): PhoneVerificationResult
    suspend fun verifyCode(verificationId: String, code: String): String
}

@Composable
expect fun rememberFirebasePhoneAuth(): FirebasePhoneAuth?

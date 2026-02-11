package com.muhabbet.auth.domain.port.`in`

interface VerifyOtpUseCase {
    suspend fun verifyOtp(phoneNumber: String, otp: String, deviceName: String, platform: String): AuthResult
}

data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val userId: String,
    val deviceId: String,
    val isNewUser: Boolean
)

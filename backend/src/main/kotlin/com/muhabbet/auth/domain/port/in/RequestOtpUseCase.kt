package com.muhabbet.auth.domain.port.`in`

interface RequestOtpUseCase {
    fun requestOtp(phoneNumber: String): OtpResult
}

data class OtpResult(
    val ttlSeconds: Int,
    val retryAfterSeconds: Int,
    val mockCode: String? = null
)

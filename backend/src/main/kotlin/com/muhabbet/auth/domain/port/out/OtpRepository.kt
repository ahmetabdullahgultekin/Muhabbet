package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.OtpRequest

interface OtpRepository {
    fun save(otpRequest: OtpRequest): OtpRequest
    fun findActiveByPhoneNumber(phoneNumber: String): OtpRequest?
    fun incrementAttempts(otpRequest: OtpRequest)
    fun markVerified(otpRequest: OtpRequest)
}

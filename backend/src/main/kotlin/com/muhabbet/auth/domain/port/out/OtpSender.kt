package com.muhabbet.auth.domain.port.out

interface OtpSender {
    suspend fun send(phoneNumber: String, otp: String)
}

package com.muhabbet.auth.domain.model

import java.time.Instant
import java.util.UUID

data class OtpRequest(
    val id: UUID = UUID.randomUUID(),
    val phoneNumber: String,
    val otpHash: String,
    val attempts: Int = 0,
    val expiresAt: Instant,
    val verified: Boolean = false,
    val createdAt: Instant = Instant.now()
)

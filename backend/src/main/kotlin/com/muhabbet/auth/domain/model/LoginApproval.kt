package com.muhabbet.auth.domain.model

import java.time.Instant
import java.util.UUID

enum class LoginApprovalStatus {
    PENDING, APPROVED, DENIED, EXPIRED
}

data class LoginApproval(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val deviceName: String? = null,
    val platform: String? = null,
    val ipAddress: String? = null,
    val status: LoginApprovalStatus = LoginApprovalStatus.PENDING,
    val createdAt: Instant = Instant.now(),
    val resolvedAt: Instant? = null,
    val expiresAt: Instant = Instant.now().plusSeconds(300)
)

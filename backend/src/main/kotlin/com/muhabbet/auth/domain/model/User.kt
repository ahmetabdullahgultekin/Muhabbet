package com.muhabbet.auth.domain.model

import java.time.Instant
import java.util.UUID

enum class UserStatus {
    ACTIVE, SUSPENDED, DELETED
}

data class User(
    val id: UUID,
    val phoneNumber: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val about: String? = null,
    val status: UserStatus = UserStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val lastSeenAt: Instant? = null,
    // Two-Step Verification
    val twoStepPinHash: String? = null,
    val twoStepEmail: String? = null,
    val twoStepEnabledAt: Instant? = null,
    // Privacy Settings
    val readReceiptsEnabled: Boolean = true,
    val onlineStatusVisibility: String = "everyone",
    val aboutVisibility: String = "everyone"
)

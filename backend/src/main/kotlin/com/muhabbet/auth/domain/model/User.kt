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
    val aboutVisibility: String = "everyone",
    /**
     * Grants the moderation review endpoints and the admin-only actuator endpoints. Granted by a
     * hand-written UPDATE (see V21) — there is no endpoint that sets it, and no request DTO carries
     * it. It lives on the domain model rather than only on the JPA entity because every profile and
     * privacy write goes through `userRepository.save(user.copy(...))`; a field the domain model did
     * not carry would be reset to false the first time an admin changed their own display name.
     */
    val isAdmin: Boolean = false
)

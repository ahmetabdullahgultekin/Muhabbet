package com.muhabbet.auth.domain.model

import java.time.Instant
import java.util.UUID

data class Device(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val platform: String,
    val deviceName: String? = null,
    val pushToken: String? = null,
    val lastActiveAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val isPrimary: Boolean = false,
    // ─── Multi-device (Tier 2, additive — defaults preserve single-device semantics) ───
    /** The primary device that approved this companion, or null for a primary/legacy device. */
    val linkedByDeviceId: UUID? = null,
    /** User-facing label shown on the "Linked devices" screen (e.g. "Chrome on macOS"). */
    val displayName: String? = null,
    /** Soft-tombstone: a revoked companion is excluded from the active device set. */
    val revokedAt: Instant? = null
) {
    /** A device participates in fan-out only while it has not been revoked. */
    val isActive: Boolean get() = revokedAt == null

    /** True when this device was linked as a companion (vs a primary / legacy single device). */
    val isCompanion: Boolean get() = linkedByDeviceId != null
}


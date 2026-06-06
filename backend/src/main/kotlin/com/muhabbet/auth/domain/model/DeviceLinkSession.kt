package com.muhabbet.auth.domain.model

import java.time.Instant
import java.util.UUID

/**
 * State of a QR device-link handshake (Tier 2 multi-device, slice S1).
 *
 * The primary device opens a session and shows [DeviceLinkSession.linkToken] inside a QR code; a
 * companion scans it and completes the handshake. Tokens are single-use and short-lived. NO key
 * material lives here — only the public, opaque [DeviceLinkSession.publicBundle] that the future
 * crypto slice ([com.muhabbet.shared.port.DeviceLinkCrypto]) will consume.
 */
enum class DeviceLinkStatus {
    PENDING, COMPLETED, EXPIRED, CANCELLED
}

data class DeviceLinkSession(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val primaryDeviceId: UUID,
    val linkToken: String,
    val status: DeviceLinkStatus = DeviceLinkStatus.PENDING,
    val companionPlatform: String? = null,
    val companionDeviceName: String? = null,
    /** Opaque PUBLIC prekey bundle supplied by the companion; consumed by the (future) crypto slice. */
    val publicBundle: String? = null,
    val linkedDeviceId: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant,
    val completedAt: Instant? = null
) {
    fun isExpired(now: Instant = Instant.now()): Boolean = expiresAt.isBefore(now)
}

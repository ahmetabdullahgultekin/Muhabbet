package com.muhabbet.auth.domain.model

import java.time.Instant
import java.util.UUID

/**
 * State of a QR device-link handshake (Tier 2 multi-device, slice S1).
 *
 * The primary device opens a session and shows a link token inside a QR code; a companion scans it
 * and completes the handshake. Tokens are single-use and short-lived. NO key material lives here —
 * only the public, opaque [DeviceLinkSession.publicBundle] that the future crypto slice
 * ([com.muhabbet.shared.port.DeviceLinkCrypto]) will consume.
 *
 * PENDING -> COMPLETED is the only stored transition (#884). EXPIRED is *derived* from
 * [DeviceLinkSession.expiresAt] by [DeviceLinkSession.effectiveStatus] rather than written: the old
 * code wrote EXPIRED and then threw inside the same transaction, so the write was always rolled back
 * and the row never said EXPIRED anyway.
 */
enum class DeviceLinkStatus {
    PENDING, COMPLETED, EXPIRED, CANCELLED
}

data class DeviceLinkSession(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val primaryDeviceId: UUID,
    /** SHA-256 of the link token. The token itself is shown once, in the QR, and never stored (#884). */
    val tokenHash: String,
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
    fun isExpired(now: Instant = Instant.now()): Boolean = !expiresAt.isAfter(now)

    /** The status as of [now]: a PENDING session past its expiry reads EXPIRED without a write. */
    fun effectiveStatus(now: Instant): DeviceLinkStatus =
        if (status == DeviceLinkStatus.PENDING && isExpired(now)) DeviceLinkStatus.EXPIRED else status

    /**
     * PENDING (and unexpired) -> COMPLETED. Pure: the caller attaches the companion's device id once
     * it exists, and the persistence write must still be conditional on the row being PENDING and
     * unexpired, because two companions can present the same token at once.
     */
    fun complete(
        now: Instant,
        companionPlatform: String,
        companionDeviceName: String?,
        publicBundle: String?
    ): DeviceLinkCompletion = when (effectiveStatus(now)) {
        DeviceLinkStatus.PENDING -> DeviceLinkCompletion.Completed(
            copy(
                status = DeviceLinkStatus.COMPLETED,
                companionPlatform = companionPlatform,
                companionDeviceName = companionDeviceName,
                publicBundle = publicBundle,
                completedAt = now
            )
        )
        DeviceLinkStatus.EXPIRED -> DeviceLinkCompletion.Expired
        DeviceLinkStatus.COMPLETED, DeviceLinkStatus.CANCELLED -> DeviceLinkCompletion.AlreadyUsed
    }
}

sealed interface DeviceLinkCompletion {
    data class Completed(val session: DeviceLinkSession) : DeviceLinkCompletion
    data object Expired : DeviceLinkCompletion
    data object AlreadyUsed : DeviceLinkCompletion
}

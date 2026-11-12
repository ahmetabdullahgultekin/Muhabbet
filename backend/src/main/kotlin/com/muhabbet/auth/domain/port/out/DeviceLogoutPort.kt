package com.muhabbet.auth.domain.port.out

import java.time.Instant
import java.util.UUID

/**
 * Records that a device signed out, so the access tokens it already holds stop working (#747).
 *
 * Revoking refresh tokens stops new access tokens; it does nothing to the one in hand, which is
 * valid for the rest of its lifetime. [SessionStatusPort.isRevoked] compares a token's `iat` with
 * the instant written here, so a token issued before the logout is refused by both the REST filter
 * and the WebSocket handshake, and one issued by a later sign-in on the same device is not.
 */
fun interface DeviceLogoutPort {
    fun recordLogout(userId: UUID, deviceId: UUID, at: Instant)
}

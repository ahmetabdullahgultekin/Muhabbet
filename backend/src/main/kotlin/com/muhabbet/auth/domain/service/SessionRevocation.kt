package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import java.time.Instant
import java.util.UUID

/**
 * The one way a session ends (#880, #923).
 *
 * A session is two credentials: refresh tokens, which live in a table, and access tokens, which are
 * self-contained and used to live until they expired. Ending one without the other is how a revoked
 * companion kept minting access tokens and a logged-out device kept REST access for fifteen
 * minutes. Every path that ends a session — logout, device revoke, refresh-token reuse, account
 * erasure, sign-in eviction — goes through here, so the two halves cannot drift apart again.
 *
 * Not transactional itself: it is always called inside the caller's transaction, so the revocation
 * commits or rolls back together with the decision that caused it.
 */
class SessionRevocation(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val deviceRepository: DeviceRepository
) {

    /** Ends every session of one device: its refresh tokens and every access token it holds. */
    fun endDeviceSessions(userId: UUID, deviceId: UUID) {
        refreshTokenRepository.revokeAllForDevice(userId, deviceId)
        deviceRepository.bumpTokenVersion(deviceId)
    }

    /** Ends one sign-in's rotation family and the device's access tokens — the reuse response. */
    fun endFamily(familyId: UUID, deviceId: UUID) {
        refreshTokenRepository.revokeFamily(familyId, Instant.now())
        deviceRepository.bumpTokenVersion(deviceId)
    }

    /** Ends every session of every device of an account — erasure and suspension. */
    fun endAllSessions(userId: UUID) {
        refreshTokenRepository.revokeAllForUser(userId)
        deviceRepository.bumpTokenVersionForUser(userId)
    }
}

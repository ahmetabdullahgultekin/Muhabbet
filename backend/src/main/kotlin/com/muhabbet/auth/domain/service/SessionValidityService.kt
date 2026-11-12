package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.port.`in`.CheckSessionValidityUseCase
import com.muhabbet.auth.domain.port.out.SessionStatusPort
import java.time.Instant
import java.util.UUID

/**
 * Refuses a validly signed token for a revoked device, an erased account, or a device that has
 * logged out since the token was issued (#747).
 *
 * Deliberately uncached. It runs once per request, and a cache would turn "revoked" into "revoked,
 * eventually" — the very gap this exists to close. The port answers it in one indexed query.
 */
class SessionValidityService(
    private val sessionStatusPort: SessionStatusPort
) : CheckSessionValidityUseCase {

    // A token without an issue time cannot prove it postdates a logout, so it is treated as the
    // oldest possible token: refused if the device ever logged out, honoured otherwise.
    override fun isSessionValid(userId: UUID, deviceId: UUID, issuedAt: Instant?): Boolean =
        !sessionStatusPort.isRevoked(userId, deviceId, issuedAt ?: Instant.EPOCH)
}

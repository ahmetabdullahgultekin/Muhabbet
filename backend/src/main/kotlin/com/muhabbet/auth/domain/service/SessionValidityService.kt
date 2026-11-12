package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.port.`in`.CheckSessionValidityUseCase
import com.muhabbet.auth.domain.port.out.SessionStatusPort
import java.util.UUID

/**
 * Refuses a validly signed token for a revoked device or an erased account (#747).
 *
 * Deliberately uncached. It runs once per request, and a cache would turn "revoked" into "revoked,
 * eventually" — the very gap this exists to close. The port answers it in one indexed query.
 */
class SessionValidityService(
    private val sessionStatusPort: SessionStatusPort
) : CheckSessionValidityUseCase {

    override fun isSessionValid(userId: UUID, deviceId: UUID): Boolean =
        !sessionStatusPort.isRevoked(userId, deviceId)
}

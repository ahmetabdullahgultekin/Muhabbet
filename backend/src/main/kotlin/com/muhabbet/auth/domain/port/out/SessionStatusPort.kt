package com.muhabbet.auth.domain.port.out

import java.time.Instant
import java.util.UUID

/**
 * The one read every authenticated request makes before it is believed (#747).
 *
 * A signed access token proves who it was issued to, not that the account or device is still in a
 * state that may use it. This answers the second question in a single round trip.
 */
fun interface SessionStatusPort {

    /**
     * True when there is positive evidence the credential must not be honoured: [userId] is
     * erased, [deviceId] has been revoked, or [deviceId] logged out at or after [issuedAt]. A row
     * that is simply absent is **not** evidence — the signature is still the proof of identity, as
     * it always was; this adds refusals, it does not add a requirement that every token map to a row.
     */
    fun isRevoked(userId: UUID, deviceId: UUID, issuedAt: Instant): Boolean
}

package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.SessionRevocationReason
import java.util.UUID

/**
 * Ends the live sessions a credential change has just made illegitimate (#747, ADR-0018).
 *
 * Revoking a refresh token or tombstoning a device row says nothing to a WebSocket that is already
 * open: it authenticated once, at connect, and stays authenticated until it drops. Without this a
 * revoked companion kept receiving every message for as long as its socket lived, and an erased
 * account went on receiving them after erasure.
 *
 * Implemented on the messaging side, which owns the sockets. The auth module only states what
 * happened; it does not know a socket exists.
 *
 * Implementations must not act before the caller's transaction commits. A close that lands first
 * invites the client to reconnect at once, and that reconnect would be authorised against the
 * not-yet-committed state — the device is still active as far as any other connection can see.
 */
interface SessionRevocationPort {

    /** Ends every live session authenticated as [deviceId] of [userId]. */
    fun revokeDeviceSessions(userId: UUID, deviceId: UUID, reason: SessionRevocationReason)

    /** Ends every live session of [userId], on every device. */
    fun revokeUserSessions(userId: UUID, reason: SessionRevocationReason)
}

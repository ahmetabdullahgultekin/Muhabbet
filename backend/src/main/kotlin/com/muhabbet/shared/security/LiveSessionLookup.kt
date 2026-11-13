package com.muhabbet.shared.security

import java.util.UUID

/**
 * What the database says about a session *now*, as opposed to what its access token said when it
 * was minted (#923).
 *
 * @property tokenVersion the device's current access-token epoch. A token stamped with any other
 *   value was issued before a logout, a device revoke or a refresh-token reuse, and is dead.
 * @property isAdmin the owner's admin flag as stored, so a demotion takes effect on the next request
 *   rather than when the token expires.
 */
data class LiveSession(val tokenVersion: Int, val isAdmin: Boolean)

/**
 * Looks up whether a (user, device) pair is still a live session. Owned by the cross-cutting
 * security layer — the filter that asks is not part of any module — and implemented by the auth
 * module's persistence adapter, which owns the devices and users tables.
 */
fun interface LiveSessionLookup {
    /**
     * The live session, or null when the device does not exist, belongs to someone else, is
     * revoked, or its account is not ACTIVE (suspended, deleted).
     */
    fun findLiveSession(userId: UUID, deviceId: UUID): LiveSession?
}

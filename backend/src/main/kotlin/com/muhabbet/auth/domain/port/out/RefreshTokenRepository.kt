package com.muhabbet.auth.domain.port.out

import java.time.Instant
import java.util.UUID

/**
 * One refresh token. [familyId] groups a sign-in's whole rotation chain (#880): the first token of
 * a sign-in starts a family (its own id), and every successor inherits it.
 *
 * @property rotatedAt set — together with [revokedAt] — when this token was exchanged for a
 *   successor. A token presented again after that is a replay, not a stale client.
 */
data class RefreshTokenRecord(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val deviceId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
    val revokedAt: Instant? = null,
    val familyId: UUID = id,
    val rotatedAt: Instant? = null
)

interface RefreshTokenRepository {
    fun save(record: RefreshTokenRecord): RefreshTokenRecord

    /**
     * The token with this hash **in any state** — active, rotated, revoked or expired. Reuse
     * detection needs to see the dead ones: a filter on "active" made a replayed token look like a
     * token that never existed.
     */
    fun findByTokenHash(tokenHash: String): RefreshTokenRecord?

    /** All sessions for the user, including expired/revoked ones — used by the KVKK data export. */
    fun findByUserId(userId: UUID): List<RefreshTokenRecord>
    fun revokeAllForDevice(userId: UUID, deviceId: UUID)
    fun revokeAllForUser(userId: UUID)

    /**
     * Retires [tokenHash] as rotated, **only if it is still live**, and reports whether this call
     * did it. One conditional UPDATE: of two concurrent refreshes with the same token, the row lock
     * lets exactly one see `true`; the other sees `false` and must treat the token as replayed.
     */
    fun markRotated(tokenHash: String, at: Instant): Boolean

    /** Revokes every still-live token of [familyId]. Idempotent. */
    fun revokeFamily(familyId: UUID, at: Instant)
}

package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.DeviceLinkSession
import java.time.Instant
import java.util.UUID

interface DeviceLinkSessionRepository {
    fun save(session: DeviceLinkSession): DeviceLinkSession
    fun findById(id: UUID): DeviceLinkSession?

    /** Looks a session up by the SHA-256 of its token; the token itself is never stored (#884). */
    fun findByTokenHash(tokenHash: String): DeviceLinkSession?

    /**
     * Writes [completed] (status COMPLETED, companion details, linked device) **only if the stored
     * row is still PENDING and unexpired at [now]**, returning whether it did. The returned flag is
     * the claim: of two companions presenting one QR at once, exactly one gets `true` (#884).
     */
    fun claimCompletion(completed: DeviceLinkSession, now: Instant): Boolean

    /** Every link handshake the user has started, regardless of status — used by the KVKK data export. */
    fun findByUserId(userId: UUID): List<DeviceLinkSession>
}

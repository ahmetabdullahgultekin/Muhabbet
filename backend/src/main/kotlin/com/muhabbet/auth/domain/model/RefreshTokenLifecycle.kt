package com.muhabbet.auth.domain.model

import java.time.Instant

/**
 * The lifecycle of one refresh token, as an explicit state machine (ADR 0012, #880).
 *
 * ```
 *            present (session live)           present again
 *  Active ─────────────────────────▶ Rotated ───────────────▶ reuse: revoke the whole family
 *    │                                  (successor issued in the same family)
 *    ├── logout / device revoke / erasure / family revoked ──▶ Revoked
 *    └── time passes ─────────────────────────────────────────▶ Expired
 * ```
 *
 * The bulk transitions to Revoked happen in SQL (one UPDATE per family, device or user); what lives
 * here is the decision taken when a client **presents** a token, which is the transition that used
 * to be wrong: a replayed token was answered like any revoked one, and both racing presenters of one
 * live token were each allowed to rotate it.
 */
sealed interface RefreshTokenState {
    data object Active : RefreshTokenState
    /** Exchanged for a successor. Seeing it again means two parties hold the same family. */
    data object Rotated : RefreshTokenState
    /** Ended by logout, device revoke, account erasure or a family revocation. */
    data object Revoked : RefreshTokenState
    data object Expired : RefreshTokenState

    companion object {
        /**
         * Rotated is checked before Revoked because a rotated token carries both timestamps; it is
         * the rotation that makes a second presentation suspicious.
         */
        fun of(revokedAt: Instant?, rotatedAt: Instant?, expiresAt: Instant, now: Instant): RefreshTokenState =
            when {
                rotatedAt != null -> Rotated
                revokedAt != null -> Revoked
                !expiresAt.isAfter(now) -> Expired
                else -> Active
            }
    }
}

/** Whether the device and account behind a token may still hold a session. */
enum class SessionStanding {
    /** Device not revoked, account ACTIVE. */
    LIVE,
    /** Device revoked or gone, or account suspended or deleted. */
    ENDED
}

/** What to do with a presented refresh token. Effects are data; the service executes them. */
sealed interface RefreshDecision {
    /** Retire the token as rotated and issue its successor in the same family. */
    data object Rotate : RefreshDecision

    /**
     * Refuse, and first revoke the token's whole family and the device's access tokens.
     * [reuseDetected] distinguishes a replay (logged as a security event) from a session that ended.
     */
    data class RevokeFamily(val reuseDetected: Boolean) : RefreshDecision

    /** Refuse with no further effect; the family is already dead or simply expired. */
    data class Reject(val reason: RefreshRejection) : RefreshDecision
}

enum class RefreshRejection { REVOKED, EXPIRED }

/**
 * Pure and total over every (state, standing) pair — see `RefreshTokenLifecycleTest`.
 *
 * A token whose session has ended is refused even when it is still Active, and its family is
 * revoked on the way out so it cannot be tried again later. A Rotated token is refused as reuse
 * whatever the standing: it is evidence of theft either way.
 */
fun RefreshTokenState.onPresented(standing: SessionStanding): RefreshDecision = when (this) {
    RefreshTokenState.Rotated -> RefreshDecision.RevokeFamily(reuseDetected = true)
    RefreshTokenState.Revoked -> RefreshDecision.Reject(RefreshRejection.REVOKED)
    RefreshTokenState.Expired -> RefreshDecision.Reject(RefreshRejection.EXPIRED)
    RefreshTokenState.Active -> when (standing) {
        SessionStanding.LIVE -> RefreshDecision.Rotate
        SessionStanding.ENDED -> RefreshDecision.RevokeFamily(reuseDetected = false)
    }
}

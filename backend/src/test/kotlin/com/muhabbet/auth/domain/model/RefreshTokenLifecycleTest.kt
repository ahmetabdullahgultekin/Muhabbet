package com.muhabbet.auth.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

/**
 * The refresh-token state machine (ADR 0012, #880): every (state, standing) pair, and how a stored
 * row maps onto a state.
 */
class RefreshTokenLifecycleTest {

    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val later = now.plusSeconds(3600)
    private val earlier = now.minusSeconds(3600)

    // ─── State derivation ────────────────────────────────

    @Test
    fun `should be Active when neither retired nor expired`() {
        assertEquals(RefreshTokenState.Active, RefreshTokenState.of(null, null, later, now))
    }

    @Test
    fun `should be Rotated when it was exchanged for a successor, whatever else is true`() {
        // A rotated row carries revoked_at as well; rotation is what makes a replay suspicious.
        assertEquals(RefreshTokenState.Rotated, RefreshTokenState.of(earlier, earlier, later, now))
        assertEquals(RefreshTokenState.Rotated, RefreshTokenState.of(earlier, earlier, earlier, now))
    }

    @Test
    fun `should be Revoked when retired without rotation`() {
        assertEquals(RefreshTokenState.Revoked, RefreshTokenState.of(earlier, null, later, now))
    }

    @Test
    fun `should be Expired at and after its expiry`() {
        assertEquals(RefreshTokenState.Expired, RefreshTokenState.of(null, null, earlier, now))
        assertEquals(RefreshTokenState.Expired, RefreshTokenState.of(null, null, now, now))
    }

    // ─── Transitions on presentation ─────────────────────

    @Test
    fun `should rotate an active token of a live session`() {
        assertEquals(RefreshDecision.Rotate, RefreshTokenState.Active.onPresented(SessionStanding.LIVE))
    }

    @Test
    fun `should revoke the family of an active token whose session has ended`() {
        // Device revoked or account suspended: refuse, and make sure it cannot be tried again.
        assertEquals(
            RefreshDecision.RevokeFamily(reuseDetected = false),
            RefreshTokenState.Active.onPresented(SessionStanding.ENDED)
        )
    }

    @ParameterizedTest
    @EnumSource(SessionStanding::class)
    fun `should treat a rotated token as reuse and revoke the family`(standing: SessionStanding) {
        assertEquals(
            RefreshDecision.RevokeFamily(reuseDetected = true),
            RefreshTokenState.Rotated.onPresented(standing)
        )
    }

    @ParameterizedTest
    @EnumSource(SessionStanding::class)
    fun `should refuse a revoked token with no further effect`(standing: SessionStanding) {
        assertEquals(
            RefreshDecision.Reject(RefreshRejection.REVOKED),
            RefreshTokenState.Revoked.onPresented(standing)
        )
    }

    @ParameterizedTest
    @EnumSource(SessionStanding::class)
    fun `should refuse an expired token as expired`(standing: SessionStanding) {
        assertEquals(
            RefreshDecision.Reject(RefreshRejection.EXPIRED),
            RefreshTokenState.Expired.onPresented(standing)
        )
    }
}

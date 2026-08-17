package com.muhabbet.messaging.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * `ConversationMember.isMuted()` is the one place #571's fix asks "is this member's mute still in
 * effect right now" — the broadcaster calls it once per offline recipient instead of re-deriving
 * the same comparison inline in two adapters. Pure domain logic, no Spring, no mocks.
 */
class ConversationMemberMuteTest {

    private fun member(mutedUntil: Instant?) = ConversationMember(
        conversationId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        mutedUntil = mutedUntil
    )

    @Test
    fun `should not be muted when mutedUntil was never set`() {
        assertFalse(member(mutedUntil = null).isMuted())
    }

    @Test
    fun `should be muted while mutedUntil is still in the future`() {
        val eightHoursOut = Instant.now().plusSeconds(8 * 3600)
        assertTrue(member(mutedUntil = eightHoursOut).isMuted())
    }

    @Test
    fun `should stop being muted once mutedUntil has passed, with no separate unmute needed`() {
        val expiredAnHourAgo = Instant.now().minusSeconds(3600)
        assertFalse(member(mutedUntil = expiredAnHourAgo).isMuted())
    }

    @Test
    fun `should treat the far-future always-mute sentinel as muted`() {
        // ConversationController encodes "always" as 2099-12-31T23:59:59Z rather than a nullable
        // "forever" flag — confirm isMuted reads that the same way it reads an 8h/1w mute.
        val alwaysSentinel = Instant.parse("2099-12-31T23:59:59Z")
        assertTrue(member(mutedUntil = alwaysSentinel).isMuted())
    }

    @Test
    fun `should evaluate against the instant it is given, not only the real clock`() {
        val mutedUntil = Instant.parse("2026-01-01T00:00:00Z")
        val justBefore = Instant.parse("2025-12-31T23:59:59Z")
        val justAfter = Instant.parse("2026-01-01T00:00:01Z")

        assertTrue(member(mutedUntil).isMuted(now = justBefore))
        assertFalse(member(mutedUntil).isMuted(now = justAfter))
    }
}

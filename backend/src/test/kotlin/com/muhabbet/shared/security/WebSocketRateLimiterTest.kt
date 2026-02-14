package com.muhabbet.shared.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class WebSocketRateLimiterTest {

    private val rateLimiter = WebSocketRateLimiter()

    @Test
    fun `should allow messages within rate limit`() {
        val userId = UUID.randomUUID()
        repeat(50) {
            assertTrue(rateLimiter.allowMessage(userId))
        }
    }

    @Test
    fun `should reject messages exceeding rate limit`() {
        val userId = UUID.randomUUID()
        repeat(50) { rateLimiter.allowMessage(userId) }
        assertFalse(rateLimiter.allowMessage(userId))
    }

    @Test
    fun `should track users independently`() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()

        repeat(50) { rateLimiter.allowMessage(userA) }
        assertTrue(rateLimiter.allowMessage(userB))
        assertFalse(rateLimiter.allowMessage(userA))
    }

    @Test
    fun `should clean up user on removal`() {
        val userId = UUID.randomUUID()
        repeat(50) { rateLimiter.allowMessage(userId) }
        assertFalse(rateLimiter.allowMessage(userId))

        rateLimiter.removeUser(userId)
        assertTrue(rateLimiter.allowMessage(userId))
    }
}

package com.muhabbet.shared.security

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-connection WebSocket rate limiter.
 * Prevents a single client from flooding the server with messages.
 * Uses a sliding window counter per userId.
 */
@Component
class WebSocketRateLimiter {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val WINDOW_MS = 10_000L          // 10-second window
        private const val MAX_MESSAGES_PER_WINDOW = 50  // 50 messages per 10 seconds (5/sec average)
    }

    private data class RateWindow(
        val count: AtomicInteger = AtomicInteger(0),
        val windowStart: AtomicLong = AtomicLong(System.currentTimeMillis())
    )

    private val windows = ConcurrentHashMap<UUID, RateWindow>()

    /**
     * Returns true if the message should be allowed, false if rate-limited.
     */
    fun allowMessage(userId: UUID): Boolean {
        val now = System.currentTimeMillis()
        val window = windows.computeIfAbsent(userId) { RateWindow() }

        // Reset window if expired
        if (now - window.windowStart.get() > WINDOW_MS) {
            window.count.set(0)
            window.windowStart.set(now)
        }

        val count = window.count.incrementAndGet()
        if (count > MAX_MESSAGES_PER_WINDOW) {
            log.warn("WebSocket rate limit exceeded for userId={}, count={}", userId, count)
            return false
        }
        return true
    }

    fun removeUser(userId: UUID) {
        windows.remove(userId)
    }
}

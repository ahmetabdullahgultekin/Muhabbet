package com.muhabbet.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple in-memory rate limiter for auth endpoints, applied per client IP.
 *
 * This is a throughput throttle, not an authorization control. It is per-instance (a
 * ConcurrentHashMap, so it does not span replicas) and it trusts `X-Forwarded-For`, so it should not
 * be relied on to bound attempts against a single account — that is the job of the per-OTP attempt
 * counter in AuthService.
 */
@Component
class RateLimitFilter(
    @Value("\${muhabbet.rate-limit.max-requests:10}")
    private val maxRequests: Int,
    @Value("\${muhabbet.rate-limit.window-seconds:60}")
    windowSeconds: Long
) : OncePerRequestFilter() {

    private val windowMs = windowSeconds * 1000L

    private data class RateWindow(
        val count: AtomicInteger = AtomicInteger(0),
        @Volatile var windowStart: Long = System.currentTimeMillis()
    )

    private val clients = ConcurrentHashMap<String, RateWindow>()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return !path.startsWith("/api/v1/auth/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val clientIp = clientAddress(request)

        val now = System.currentTimeMillis()
        evictExpired(now)
        val window = clients.computeIfAbsent(clientIp) { RateWindow() }

        if (now - window.windowStart > windowMs) {
            window.count.set(0)
            window.windowStart = now
        }

        if (window.count.incrementAndGet() > maxRequests) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write("""{"error":{"code":"RATE_LIMITED","message":"Too many requests"},"timestamp":"${java.time.Instant.now()}"}""")
            return
        }

        filterChain.doFilter(request, response)
    }

    /**
     * Takes the **rightmost** `X-Forwarded-For` entry, not the leftmost.
     *
     * The leftmost entry is whatever the client sent, so keying on it let anyone mint a fresh
     * budget per request by varying a header (#270). The rightmost is the one appended by the proxy
     * closest to us, which a client cannot forge without being that proxy.
     *
     * Measured against production: Traefik currently *replaces* the header rather than appending,
     * so leftmost and rightmost are the same value and this changes nothing today. It matters the
     * moment that is not true — a second proxy, a different edge, or a Traefik configuration change
     * — and the previous code was correct only by accident of what happened to sit in front of it.
     *
     * `remoteAddr` remains the fallback for a direct connection with no proxy at all.
     */
    private fun clientAddress(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.map { it.trim() }
            ?.lastOrNull { it.isNotEmpty() }
            ?: request.remoteAddr

    /**
     * The map had no eviction, so every distinct client address stayed for the life of the process.
     * Sweeping on write keeps it bounded by *active* clients rather than by all clients ever seen,
     * and costs nothing on the common path because it only runs once the map is large enough to
     * matter.
     */
    private fun evictExpired(now: Long) {
        if (clients.size < EVICTION_THRESHOLD) return
        clients.entries.removeIf { now - it.value.windowStart > windowMs * 2 }
    }

    private companion object {
        /** Below this, sweeping costs more than the memory it reclaims. */
        const val EVICTION_THRESHOLD = 1_000
    }
}

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
        val clientIp = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr

        val window = clients.computeIfAbsent(clientIp) { RateWindow() }
        val now = System.currentTimeMillis()

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
}

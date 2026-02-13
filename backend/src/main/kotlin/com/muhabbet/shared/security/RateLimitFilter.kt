package com.muhabbet.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple in-memory rate limiter for auth endpoints.
 * Limits per IP: 10 requests per minute for OTP/auth endpoints.
 */
@Component
class RateLimitFilter : OncePerRequestFilter() {

    companion object {
        private const val MAX_REQUESTS = 10
        private const val WINDOW_MS = 60_000L
    }

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

        if (now - window.windowStart > WINDOW_MS) {
            window.count.set(0)
            window.windowStart = now
        }

        if (window.count.incrementAndGet() > MAX_REQUESTS) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write("""{"error":{"code":"RATE_LIMITED","message":"Too many requests"},"timestamp":"${java.time.Instant.now()}"}""")
            return
        }

        filterChain.doFilter(request, response)
    }
}

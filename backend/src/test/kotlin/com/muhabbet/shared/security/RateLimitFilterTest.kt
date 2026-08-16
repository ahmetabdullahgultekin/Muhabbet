package com.muhabbet.shared.security

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * The header handling in #270: keying on the **leftmost** `X-Forwarded-For` entry let anyone mint a
 * fresh budget per request by varying a header they control.
 *
 * Measured against production, Traefik replaces the header rather than appending, so the bypass did
 * not work as deployed — but the code was correct only by accident of what sat in front of it, and
 * a second proxy or a configuration change would have restored the hole silently. These tests pin
 * the behaviour so it cannot drift back.
 */
class RateLimitFilterTest {

    private val maxRequests = 3
    private val filter = RateLimitFilter(maxRequests = maxRequests, windowSeconds = 60)

    /** Returns the status after sending one request with the given forwarded-for header. */
    private fun send(forwardedFor: String?, remoteAddr: String = "10.0.0.1"): Int {
        val request = MockHttpServletRequest("POST", "/api/v1/auth/otp/request")
        request.remoteAddr = remoteAddr
        forwardedFor?.let { request.addHeader("X-Forwarded-For", it) }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, FilterChain { _, _ -> })
        return response.status
    }

    @Test
    fun `a spoofed leading address cannot buy a fresh budget`() {
        // The shape a proxy that appends produces: "<what the client claimed>, <the real address>".
        // Keying on the claimed half is the bypass; keying on the appended half is not forgeable
        // without being the proxy.
        repeat(maxRequests) { i ->
            assertEquals(200, send("203.0.113.$i, 198.51.100.7"), "request ${i + 1} should pass")
        }

        assertEquals(
            429,
            send("203.0.113.99, 198.51.100.7"),
            "a new leading address bought another request, so the header is still trusted"
        )
    }

    @Test
    fun `distinct real clients still get their own budget`() {
        // The fix must not collapse every client behind the proxy into one bucket — that would
        // rate-limit the whole user base as if it were a single caller.
        repeat(maxRequests) { assertEquals(200, send("203.0.113.1, 198.51.100.7")) }
        assertEquals(429, send("203.0.113.1, 198.51.100.7"))

        assertEquals(200, send("203.0.113.1, 198.51.100.8"), "a different real client was blocked")
    }

    @Test
    fun `falls back to the socket address when there is no proxy`() {
        repeat(maxRequests) { assertEquals(200, send(forwardedFor = null, remoteAddr = "10.0.0.5")) }
        assertEquals(429, send(forwardedFor = null, remoteAddr = "10.0.0.5"))
        assertEquals(200, send(forwardedFor = null, remoteAddr = "10.0.0.6"))
    }

    @Test
    fun `non-auth paths are not throttled`() {
        val request = MockHttpServletRequest("GET", "/api/v1/conversations")
        var reached = false
        val response = MockHttpServletResponse()
        repeat(maxRequests * 3) {
            filter.doFilter(request, response, FilterChain { _, _ -> reached = true })
        }
        assertEquals(200, response.status)
        assertEquals(true, reached)
    }
}

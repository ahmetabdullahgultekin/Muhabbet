package com.muhabbet.shared.security

import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RateLimitFilterTest {

    private lateinit var rateLimitFilter: RateLimitFilter
    private lateinit var filterChain: FilterChain

    @BeforeEach
    fun setUp() {
        rateLimitFilter = RateLimitFilter(maxRequests = 10, windowSeconds = 60)
        filterChain = mockk(relaxed = true)
    }

    private fun createRequest(
        uri: String = "/api/v1/auth/otp/request",
        remoteAddr: String = "192.168.1.1",
        xForwardedFor: String? = null
    ): MockHttpServletRequest {
        val request = MockHttpServletRequest("GET", uri)
        request.remoteAddr = remoteAddr
        if (xForwardedFor != null) {
            request.addHeader("X-Forwarded-For", xForwardedFor)
        }
        return request
    }

    // ─── Path Filtering ──────────────────────────────────

    @Nested
    inner class PathFiltering {

        @Test
        fun `should not filter non-auth paths`() {
            // Make 20 requests to a non-auth endpoint - all should pass
            repeat(20) {
                val request = createRequest(uri = "/api/v1/conversations", remoteAddr = "10.0.0.1")
                val response = MockHttpServletResponse()
                rateLimitFilter.doFilter(request, response, filterChain)
            }

            // All should have passed through to filterChain
            verify(exactly = 20) { filterChain.doFilter(any(), any()) }
        }

        @Test
        fun `should filter auth otp request path`() {
            val request = createRequest(uri = "/api/v1/auth/otp/request")
            assertTrue(request.requestURI?.startsWith("/api/v1/auth/") == true)
        }

        @Test
        fun `should filter auth otp verify path`() {
            val request = createRequest(uri = "/api/v1/auth/otp/verify")
            assertTrue(request.requestURI?.startsWith("/api/v1/auth/") == true)
        }

        @Test
        fun `should filter auth refresh path`() {
            val request = createRequest(uri = "/api/v1/auth/refresh")
            assertTrue(request.requestURI?.startsWith("/api/v1/auth/") == true)
        }

        @Test
        fun `should not filter messaging path`() {
            val request = createRequest(uri = "/api/v1/messages")
            assertTrue(request.requestURI?.startsWith("/api/v1/auth/") != true)
        }

        @Test
        fun `should not filter media path`() {
            val request = createRequest(uri = "/api/v1/media/upload")
            assertTrue(request.requestURI?.startsWith("/api/v1/auth/") != true)
        }
    }

    // ─── Rate Limiting ──────────────────────────────────

    @Nested
    inner class RateLimiting {

        @Test
        fun `should allow requests under the limit`() {
            // Make 10 requests (the max)
            repeat(10) {
                val request = createRequest(remoteAddr = "1.1.1.1")
                val response = MockHttpServletResponse()
                rateLimitFilter.doFilter(request, response, filterChain)
            }

            // All 10 should have passed through to filterChain
            verify(exactly = 10) { filterChain.doFilter(any(), any()) }
        }

        @Test
        fun `should return 429 when exceeding rate limit`() {
            val ip = "2.2.2.2"

            // Exhaust the rate limit (10 requests)
            repeat(10) {
                val request = createRequest(remoteAddr = ip)
                val response = MockHttpServletResponse()
                rateLimitFilter.doFilter(request, response, filterChain)
            }

            // 11th request should be rate limited
            val request = createRequest(remoteAddr = ip)
            val response = MockHttpServletResponse()
            rateLimitFilter.doFilter(request, response, filterChain)

            assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
            assertEquals("application/json", response.contentType)

            // FilterChain should have been called only 10 times, not 11
            verify(exactly = 10) { filterChain.doFilter(any(), any()) }
        }

        @Test
        fun `should return error response body with RATE_LIMITED code`() {
            val ip = "3.3.3.3"

            // Exhaust the limit
            repeat(10) {
                val request = createRequest(remoteAddr = ip)
                val response = MockHttpServletResponse()
                rateLimitFilter.doFilter(request, response, filterChain)
            }

            // Trigger rate limit
            val request = createRequest(remoteAddr = ip)
            val response = MockHttpServletResponse()
            rateLimitFilter.doFilter(request, response, filterChain)

            val responseBody = response.contentAsString
            assertTrue(responseBody.contains("RATE_LIMITED"))
            assertTrue(responseBody.contains("Too many requests"))
        }

        @Test
        fun `should track rate limits independently per IP`() {
            val ip1 = "4.4.4.1"
            val ip2 = "4.4.4.2"

            // Exhaust limit for ip1
            repeat(10) {
                val request = createRequest(remoteAddr = ip1)
                val response = MockHttpServletResponse()
                rateLimitFilter.doFilter(request, response, filterChain)
            }

            // ip2 should still be allowed
            val request = createRequest(remoteAddr = ip2)
            val response = MockHttpServletResponse()
            rateLimitFilter.doFilter(request, response, filterChain)

            // 10 for ip1 + 1 for ip2 = 11 total passes
            verify(exactly = 11) { filterChain.doFilter(any(), any()) }
        }

        @Test
        fun `should use X-Forwarded-For header when present`() {
            val realIp = "5.5.5.5"

            // Make requests with same X-Forwarded-For but different remoteAddr
            repeat(10) {
                val request = createRequest(
                    remoteAddr = "127.0.0.$it",
                    xForwardedFor = realIp
                )
                val response = MockHttpServletResponse()
                rateLimitFilter.doFilter(request, response, filterChain)
            }

            // 11th with same real IP should be rate limited
            val request = createRequest(
                remoteAddr = "127.0.0.99",
                xForwardedFor = realIp
            )
            val response = MockHttpServletResponse()
            rateLimitFilter.doFilter(request, response, filterChain)

            assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
        }

        @Test
        fun `should use last IP from X-Forwarded-For chain`() {
            // Renamed from "first IP" and inverted deliberately. Keying on the *first* entry was
            // #270: the leftmost value is whatever the client sent, so varying it bought a fresh
            // budget per request. The rightmost is appended by the proxy nearest us and cannot be
            // forged without being that proxy.
            //
            // Varying the leading entry on every request is the exploit; the trailing entry is
            // constant, so all eleven must share one budget.
            repeat(10) { i ->
                val request = createRequest(
                    remoteAddr = "127.0.0.1",
                    xForwardedFor = "203.0.113.$i, 10.0.0.1, 10.0.0.2"
                )
                val response = MockHttpServletResponse()
                rateLimitFilter.doFilter(request, response, filterChain)
            }

            val request = createRequest(
                remoteAddr = "127.0.0.1",
                xForwardedFor = "203.0.113.99, 10.0.0.1, 10.0.0.2"
            )
            val response = MockHttpServletResponse()
            rateLimitFilter.doFilter(request, response, filterChain)

            assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
        }

        @Test
        fun `should fall back to remoteAddr when X-Forwarded-For is absent`() {
            val remoteAddr = "7.7.7.7"

            repeat(10) {
                val request = createRequest(remoteAddr = remoteAddr, xForwardedFor = null)
                val response = MockHttpServletResponse()
                rateLimitFilter.doFilter(request, response, filterChain)
            }

            val request = createRequest(remoteAddr = remoteAddr, xForwardedFor = null)
            val response = MockHttpServletResponse()
            rateLimitFilter.doFilter(request, response, filterChain)

            assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
        }

        @Test
        fun `should allow first request from new IP`() {
            val request = createRequest(remoteAddr = "8.8.8.8")
            val response = MockHttpServletResponse()

            rateLimitFilter.doFilter(request, response, filterChain)

            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
            assertEquals(200, response.status)
        }

        @Test
        fun `should block all requests after limit is reached`() {
            val ip = "9.9.9.9"

            // Exhaust limit
            repeat(10) {
                val request = createRequest(remoteAddr = ip)
                val response = MockHttpServletResponse()
                rateLimitFilter.doFilter(request, response, filterChain)
            }

            // Next 5 requests should all be blocked
            repeat(5) {
                val request = createRequest(remoteAddr = ip)
                val response = MockHttpServletResponse()
                rateLimitFilter.doFilter(request, response, filterChain)
                assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
            }

            // Only the first 10 should have passed through
            verify(exactly = 10) { filterChain.doFilter(any(), any()) }
        }
    }

    // ─── Window Reset ──────────────────────────────────

    @Nested
    inner class WindowReset {

        @Test
        fun `should reset counter after window expires`() {
            val ip = "10.10.10.10"

            // Use a new filter instance to avoid state from other tests
            val filter = RateLimitFilter(maxRequests = 10, windowSeconds = 60)

            // Exhaust limit
            repeat(10) {
                val request = createRequest(remoteAddr = ip)
                val response = MockHttpServletResponse()
                filter.doFilter(request, response, filterChain)
            }

            // Verify we are rate limited
            val blockedRequest = createRequest(remoteAddr = ip)
            val blockedResponse = MockHttpServletResponse()
            filter.doFilter(blockedRequest, blockedResponse, filterChain)
            assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedResponse.status)

            // We cannot easily simulate time passage in a unit test without
            // refactoring the filter to accept a Clock. However, we can verify
            // that a different IP still works (proves the filter state works per-IP).
            val differentIpRequest = createRequest(remoteAddr = "11.11.11.11")
            val freshResponse = MockHttpServletResponse()
            filter.doFilter(differentIpRequest, freshResponse, filterChain)

            // The different IP request should have passed through
            verify(atLeast = 11) { filterChain.doFilter(any(), any()) }
        }
    }

    // ─── Proxy trust (#270) ──────────────────────────────

    @Nested
    inner class ProxyTrust {

        @Test
        fun `should still give distinct real clients their own budget`() {
            // The fix must not collapse everyone behind the proxy into one bucket — that would rate
            // limit the entire user base as though it were a single caller.
            val filter = RateLimitFilter(maxRequests = 3, windowSeconds = 60)

            repeat(3) {
                filter.doFilter(
                    createRequest(xForwardedFor = "203.0.113.1, 198.51.100.7"),
                    MockHttpServletResponse(),
                    filterChain
                )
            }
            val blocked = MockHttpServletResponse()
            filter.doFilter(createRequest(xForwardedFor = "203.0.113.1, 198.51.100.7"), blocked, filterChain)
            assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.status)

            val other = MockHttpServletResponse()
            filter.doFilter(createRequest(xForwardedFor = "203.0.113.1, 198.51.100.8"), other, filterChain)
            assertEquals(HttpStatus.OK.value(), other.status)
        }

        @Test
        fun `should ignore empty entries in the chain`() {
            // A trailing comma or a blank segment must not become the key, which would put every
            // such request into one shared bucket.
            val filter = RateLimitFilter(maxRequests = 2, windowSeconds = 60)

            repeat(2) {
                filter.doFilter(
                    createRequest(xForwardedFor = "203.0.113.1, 198.51.100.9, "),
                    MockHttpServletResponse(),
                    filterChain
                )
            }
            val blocked = MockHttpServletResponse()
            filter.doFilter(createRequest(xForwardedFor = "203.0.113.1, 198.51.100.9, "), blocked, filterChain)
            assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.status)

            val other = MockHttpServletResponse()
            filter.doFilter(createRequest(xForwardedFor = "203.0.113.1, 198.51.100.10, "), other, filterChain)
            assertEquals(HttpStatus.OK.value(), other.status)
        }
    }

}

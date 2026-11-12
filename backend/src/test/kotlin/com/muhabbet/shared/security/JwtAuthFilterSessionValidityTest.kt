package com.muhabbet.shared.security

import com.muhabbet.auth.domain.port.`in`.CheckSessionValidityUseCase
import com.muhabbet.auth.domain.service.SessionValidityService
import com.muhabbet.auth.domain.port.out.SessionStatusPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID

/**
 * #747: a validly signed access token used to be honoured for its whole lifetime, whatever happened
 * to the device or account it named. The filter now asks whether the session is still allowed.
 */
class JwtAuthFilterSessionValidityTest {

    private val provider = JwtProvider(
        JwtProperties(
            secret = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256",
            accessTokenExpiry = 900,
            refreshTokenExpiry = 2592000,
            issuer = "muhabbet-test"
        ),
        MockEnvironment()
    )

    private val userId: UUID = UUID.randomUUID()
    private val deviceId: UUID = UUID.randomUUID()

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun filterWith(validity: CheckSessionValidityUseCase) {
        val token = provider.generateAccessToken(userId, deviceId)
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        JwtAuthFilter(provider, validity).doFilter(request, MockHttpServletResponse(), MockFilterChain())
    }

    @Test
    fun `should authenticate the request when the session is still valid`() {
        filterWith { _, _ -> true }

        val principal = SecurityContextHolder.getContext().authentication?.principal as? JwtClaims
        assertNotNull(principal)
        assertEquals(userId, principal?.userId)
    }

    @Test
    fun `should leave the request unauthenticated when the device was revoked or the user deleted`() {
        val revokedStatus = SessionStatusPort { _, _ -> true }

        filterWith(SessionValidityService(revokedStatus))

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `should ask about exactly the token's user and device`() {
        var asked: Pair<UUID, UUID>? = null

        filterWith { u, d -> asked = u to d; true }

        assertEquals(userId to deviceId, asked)
    }

    @Test
    fun `should carry the token expiry in the claims when the token is validated`() {
        val before = Instant.now()
        val claims = provider.validateToken(provider.generateAccessToken(userId, deviceId))
        val expiresAt = claims?.expiresAt

        assertNotNull(expiresAt)
        val seconds = (expiresAt ?: before).epochSecond - before.epochSecond
        assertTrue(seconds in 899..900, "exp should be ~900 s after issue, was $seconds")
    }
}

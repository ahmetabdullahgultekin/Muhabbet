package com.muhabbet.shared.security

import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * The `admin` claim, end to end (#551).
 *
 * `validateToken` had always read `claims["admin"]`; `generateAccessToken` had never written one.
 * So `isAdmin` was false in every token ever issued, `requireAdmin()` could only throw, and every
 * report a user filed sat unread — the review workflow was unreachable by construction, not by
 * configuration. These tests walk the whole chain rather than asserting the claim serializes:
 * generate → validate → `requireAdmin()`, and the same for the ROLE_ADMIN grant that
 * `SecurityConfig` gates `/actuator/metrics` and `/actuator/prometheus` on (#303).
 */
class JwtAdminClaimTest {

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

    private fun authenticateWith(claims: JwtClaims) {
        val authorities = if (claims.isAdmin) listOf(SimpleGrantedAuthority("ROLE_ADMIN")) else emptyList()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(claims, null, authorities)
    }

    @Test
    fun `should carry the admin claim through a token minted for an admin`() {
        val claims = provider.validateToken(provider.generateAccessToken(userId, deviceId, isAdmin = true))

        assertNotNull(claims)
        assertEquals(userId, claims?.userId)
        assertTrue(claims?.isAdmin == true)
    }

    @Test
    fun `should not carry the admin claim through a token minted for an ordinary user`() {
        val claims = provider.validateToken(provider.generateAccessToken(userId, deviceId, isAdmin = false))

        assertNotNull(claims)
        assertFalse(claims?.isAdmin ?: true)
    }

    @Test
    fun `should default to not admin when the flag is omitted`() {
        // The default matters: a call site that forgets the argument must mint a token without
        // privilege rather than one with it.
        val claims = provider.validateToken(provider.generateAccessToken(userId, deviceId))

        assertFalse(claims?.isAdmin ?: true)
    }

    @Test
    fun `should let requireAdmin through for an admin token`() {
        val claims = provider.validateToken(provider.generateAccessToken(userId, deviceId, isAdmin = true))
        checkNotNull(claims)
        authenticateWith(claims)

        // This is the assertion the moderation review endpoints depend on. Before #551 it could
        // not pass for anybody, so getPendingReports and resolveReport were dead endpoints.
        assertDoesNotThrow { AuthenticatedUser.requireAdmin() }
    }

    @Test
    fun `should refuse requireAdmin for an ordinary token`() {
        val claims = provider.validateToken(provider.generateAccessToken(userId, deviceId))
        checkNotNull(claims)
        authenticateWith(claims)

        val ex = assertThrows<BusinessException> { AuthenticatedUser.requireAdmin() }
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.errorCode)
    }

    @Test
    fun `should grant ROLE_ADMIN only to an admin token`() {
        // What SecurityConfig's hasRole("ADMIN") on /actuator/metrics and /actuator/prometheus
        // resolves against — the metrics half of #303.
        val adminClaims = provider.validateToken(provider.generateAccessToken(userId, deviceId, isAdmin = true))
        checkNotNull(adminClaims)
        authenticateWith(adminClaims)
        assertTrue(grantedAuthorities().any { it == "ROLE_ADMIN" })

        val plainClaims = provider.validateToken(provider.generateAccessToken(userId, deviceId))
        checkNotNull(plainClaims)
        authenticateWith(plainClaims)
        assertTrue(grantedAuthorities().isEmpty())
    }

    private fun grantedAuthorities(): List<String> =
        SecurityContextHolder.getContext().authentication
            ?.authorities
            ?.mapNotNull { it.authority }
            .orEmpty()
}

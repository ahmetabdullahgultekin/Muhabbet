package com.muhabbet.shared.security

import com.muhabbet.shared.AuthTestFixtures
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * The REST filter, end to end through the production [JwtAuthFilter] (#923, and #551 before it).
 *
 * A valid signature used to be the whole check, so a revoked device, a logged-out session or a
 * deleted account kept REST access until the token expired, and the `admin` flag baked into the
 * token outlived a demotion. These drive the real filter with a [LiveSessionLookup] standing in for
 * the database, and assert what it put in the SecurityContext — never a context seeded by hand,
 * which would only test the test.
 */
class JwtAuthFilterSessionTest {

    private val provider = AuthTestFixtures.jwtProvider()
    private val userId: UUID = UUID.randomUUID()
    private val deviceId: UUID = UUID.randomUUID()

    /** What "the database" currently says about [deviceId]; null = no live session. */
    private var live: LiveSession? = LiveSession(tokenVersion = 0, isAdmin = false)
    private val lookup = LiveSessionLookup { user, device ->
        if (user == userId && device == deviceId) live else null
    }

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun token(tokenVersion: Int = 0) = provider.generateAccessToken(userId, deviceId, tokenVersion)

    /** Runs a bearer token through the production filter; returns the principal it set, if any. */
    private fun filter(token: String): JwtClaims? {
        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        JwtAuthFilter(provider, lookup).doFilter(request, MockHttpServletResponse(), MockFilterChain())
        return SecurityContextHolder.getContext().authentication?.principal as? JwtClaims
    }

    private fun grantedAuthorities(): List<String> =
        SecurityContextHolder.getContext().authentication
            ?.authorities
            ?.mapNotNull { it.authority }
            .orEmpty()

    @Test
    fun `should authenticate a token whose session is live at the same epoch`() {
        val claims = filter(token())

        assertEquals(userId, claims?.userId)
        assertEquals(deviceId, claims?.deviceId)
    }

    @Test
    fun `should refuse a token minted before the device's epoch moved`() {
        // Logout, device revoke and refresh-token reuse all bump the epoch.
        live = LiveSession(tokenVersion = 1, isAdmin = false)

        assertNull(filter(token(tokenVersion = 0)))
    }

    @Test
    fun `should refuse a token whose device or account no longer has a live session`() {
        // Revoked device, suspended or deleted account: the lookup finds no live row.
        live = null

        assertNull(filter(token()))
    }

    @Test
    fun `should grant ROLE_ADMIN from the database, not from the token`() {
        // What SecurityConfig's hasRole("ADMIN") on /actuator/metrics and /actuator/prometheus
        // resolves against — the metrics half of #303.
        live = LiveSession(tokenVersion = 0, isAdmin = true)
        val sameToken = token()

        filter(sameToken)
        assertTrue(grantedAuthorities().contains("ROLE_ADMIN"))

        // Demoted between two requests carrying the very same token: the next request has no role.
        live = LiveSession(tokenVersion = 0, isAdmin = false)
        filter(sameToken)
        assertTrue(grantedAuthorities().isEmpty())
    }

    @Test
    fun `should let requireAdmin through for a live admin and refuse it once demoted`() {
        val sameToken = token()
        live = LiveSession(tokenVersion = 0, isAdmin = true)
        filter(sameToken)
        // This is the assertion the moderation review endpoints depend on (#551).
        assertDoesNotThrow { AuthenticatedUser.requireAdmin() }

        live = LiveSession(tokenVersion = 0, isAdmin = false)
        filter(sameToken)
        val ex = assertThrows<BusinessException> { AuthenticatedUser.requireAdmin() }
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.errorCode)
    }

    @Test
    fun `should leave a request with a forged signature unauthenticated without asking the database`() {
        var asked = false
        val countingLookup = LiveSessionLookup { _, _ -> asked = true; live }
        val forged = token().dropLast(2) + "xx"

        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $forged") }
        JwtAuthFilter(provider, countingLookup).doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(false, asked)
    }
}

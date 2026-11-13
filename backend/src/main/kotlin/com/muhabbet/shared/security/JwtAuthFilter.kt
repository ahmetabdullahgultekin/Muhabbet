package com.muhabbet.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates a REST request from its bearer token — and from the database (#923).
 *
 * A valid signature used to be enough, so a revoked device, a logged-out session or a deleted
 * account kept full REST access until the token expired, and a demoted admin kept their role just
 * as long. Now the token only *names* a session; [LiveSessionLookup] says whether that session
 * still exists, at which epoch, and with which privilege. One primary-key join per request — no
 * cache, deliberately: a cache is a window in which a revoked token still works, and closing that
 * window needs after-commit eviction across instances, which is more machinery than one indexed
 * lookup costs at this scale.
 *
 * A request whose token fails either check is simply left unauthenticated; the entry point then
 * answers 401, which is what makes the client refresh (or sign in again).
 */
@Component
class JwtAuthFilter(
    private val jwtProvider: JwtProvider,
    private val liveSessionLookup: LiveSessionLookup
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        authenticate(request)?.let { claims ->
            // Grant ROLE_ADMIN to admins so SecurityConfig can gate admin-only endpoints
            // (e.g. /actuator/metrics, /actuator/prometheus) with hasRole("ADMIN").
            val authorities = if (claims.isAdmin) listOf(SimpleGrantedAuthority("ROLE_ADMIN")) else emptyList()
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(claims, null, authorities)
        }
        filterChain.doFilter(request, response)
    }

    /** The request's claims with the live admin flag, or null when it is not a live session. */
    private fun authenticate(request: HttpServletRequest): JwtClaims? {
        val token = extractBearerToken(request) ?: return null
        val claims = jwtProvider.validateToken(token) ?: return null
        val session = liveSessionLookup.findLiveSession(claims.userId, claims.deviceId) ?: return null
        if (session.tokenVersion != claims.tokenVersion) return null
        return claims.copy(isAdmin = session.isAdmin)
    }

    private fun extractBearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ", ignoreCase = true)) {
            header.substring(7).trim()
        } else {
            null
        }
    }
}

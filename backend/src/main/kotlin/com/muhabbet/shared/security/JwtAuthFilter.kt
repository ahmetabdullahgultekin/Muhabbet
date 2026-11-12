package com.muhabbet.shared.security

import com.muhabbet.auth.domain.port.`in`.CheckSessionValidityUseCase
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtProvider: JwtProvider,
    private val checkSessionValidityUseCase: CheckSessionValidityUseCase
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractBearerToken(request)

        if (token != null) {
            // A valid signature is not enough on its own (#747): a token outlives the device it was
            // issued to and the account that owned it, by up to its full lifetime. Left
            // unauthenticated, the request falls to the entry point and is answered 401, which is
            // what makes the client refresh — and the refresh is refused too, which is the point.
            val claims = jwtProvider.validateToken(token)
                ?.takeIf { checkSessionValidityUseCase.isSessionValid(it.userId, it.deviceId, it.issuedAt) }
            if (claims != null) {
                // Grant ROLE_ADMIN to admin tokens so SecurityConfig can gate admin-only endpoints
                // (e.g. /actuator/metrics, /actuator/prometheus) with hasRole("ADMIN").
                val authorities = if (claims.isAdmin) {
                    listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
                } else {
                    emptyList()
                }
                val authentication = UsernamePasswordAuthenticationToken(
                    claims,
                    null,
                    authorities
                )
                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
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

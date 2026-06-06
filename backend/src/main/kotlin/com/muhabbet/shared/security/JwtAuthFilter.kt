package com.muhabbet.shared.security

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
    private val jwtProvider: JwtProvider
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractBearerToken(request)

        if (token != null) {
            val claims = jwtProvider.validateToken(token)
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

package com.muhabbet.shared.security

import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.web.ErrorResponseWriter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

/**
 * 401 with the project envelope and `AUTH_UNAUTHORIZED` (#915).
 *
 * The status is what the mobile Ktor `Auth` plugin keys its token refresh on, so it stays 401 —
 * see the "Spring Security 403 vs 401" note in CLAUDE.md. What changes is the body: it used to be
 * empty (`HttpStatusEntryPoint`), which is the one response shape `ApiClient` cannot decode.
 */
@Component
class ApiAuthenticationEntryPoint(
    private val errorWriter: ErrorResponseWriter
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) = errorWriter.write(response, ErrorCode.AUTH_UNAUTHORIZED)
}

/** 403 with the project envelope and `AUTH_FORBIDDEN`, instead of Spring's default error body (#915). */
@Component
class ApiAccessDeniedHandler(
    private val errorWriter: ErrorResponseWriter
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) = errorWriter.write(response, ErrorCode.AUTH_FORBIDDEN)
}

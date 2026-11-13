package com.muhabbet.shared.web

import com.muhabbet.shared.dto.ApiError
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.exception.ErrorCode
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * The one writer of the error envelope for code that runs **outside** a controller (#915).
 *
 * `GlobalExceptionHandler` only sees exceptions that escape a handler method. A servlet filter, the
 * Spring Security entry point and the access-denied handler all answer before any controller runs,
 * so each of them used to invent its own response: an empty 401 body from `HttpStatusEntryPoint`,
 * Spring's own default 403, and a hand-written 429 string literal in `RateLimitFilter`. The mobile
 * `ApiClient` decides what to do from `error.code`, so three shapes for three statuses meant it
 * could not tell an expired token from a forbidden request from a throttled one by the one field it
 * reads.
 *
 * Rendering goes through the same `ApiResponse` serializer the controllers use, so the bytes cannot
 * drift from the documented envelope the way a string template can. The message is always the
 * code's own `defaultMessage` unless the caller has a server-authored one — never exception text.
 */
@Component
class ErrorResponseWriter(private val json: Json) {

    fun write(
        response: HttpServletResponse,
        code: ErrorCode,
        message: String = code.defaultMessage,
        retryAfter: Duration? = null
    ) {
        response.status = code.httpStatus.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        retryAfter?.let { response.setHeader(RETRY_AFTER, retryAfterSeconds(it).toString()) }
        response.writer.write(render(code, message))
        response.writer.flush()
    }

    fun render(code: ErrorCode, message: String = code.defaultMessage): String =
        json.encodeToString(
            ApiResponse.serializer(String.serializer()),
            ApiResponse(error = ApiError(code = code.name, message = message), timestamp = Instant.now().toString())
        )

    companion object {
        const val RETRY_AFTER = "Retry-After"

        fun envelope(code: ErrorCode, message: String = code.defaultMessage): ApiResponse<Nothing> =
            ApiResponse(
                error = ApiError(code = code.name, message = message),
                timestamp = Instant.now().toString()
            )

        /** RFC 9110 §10.2.3: delay-seconds, a non-negative integer, rounded up so a client never retries early. */
        fun retryAfterSeconds(delay: Duration): Long =
            maxOf(1L, (delay.toMillis() + 999) / 1000)
    }
}

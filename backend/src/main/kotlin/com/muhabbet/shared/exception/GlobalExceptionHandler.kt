package com.muhabbet.shared.exception

import com.muhabbet.shared.dto.ApiError
import com.muhabbet.shared.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

/**
 * Turns every exception that escapes a controller into the project's error envelope.
 *
 * This advice is registered on `ExceptionHandlerExceptionResolver`, which the `DispatcherServlet`
 * consults **before** Spring's own `DefaultHandlerExceptionResolver`. So an `@ExceptionHandler`
 * here does not merely add behaviour — it *replaces* Spring's correct status mapping for every
 * exception type it matches. A bare `@ExceptionHandler(Exception::class)` therefore captures the
 * whole Spring MVC client-error family and answers 500 for all of it (#472).
 *
 * The rule that follows from that: every protocol-level client mistake needs an arm of its own,
 * with the status Spring would have chosen and a log level that says "the caller got it wrong",
 * so that [handleUnexpected] keeps meaning what it says.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Business error: {} - {}", ex.errorCode, ex.message)
        return respond(ex.errorCode, ex.message)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation error: {}", message)
        return respond(ErrorCode.VALIDATION_ERROR, message)
    }

    /**
     * A path variable or query parameter whose text Spring could not convert to the type the
     * mapping declares — `?startDate=yarın`, or an id that is not a UUID.
     *
     * Split out of [handleBadRequest] so the answer can say **which** value was wrong (#401). Its
     * third complaint was not the status alone but that `INTERNAL_ERROR` "gave no hint that the
     * field was named wrong"; a 400 whose body reads only "Doğrulama hatası" repeats that failure
     * one status code down, and the caller is still reduced to reading the container log.
     *
     * The parameter's name and the type it needed are facts about our own mapping, so naming them
     * discloses nothing. The offending **value** is deliberately not echoed back: it is unbounded
     * attacker-controlled text, and a client that sent it already knows what it sent.
     *
     * No prose is invented here for the same reason [handleValidation] invents none — the detail is
     * `name: Type`, which needs no translation and cannot drift out of step with a locale.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Nothing>> {
        val requiredType = ex.requiredType?.simpleName
        log.warn("Type mismatch on request value: {} (required {})", ex.name, requiredType)
        return respond(
            ErrorCode.VALIDATION_ERROR,
            requiredType?.let { "${ex.name}: $it" } ?: ex.name
        )
    }

    /**
     * A value the JVM rejected inside the controller rather than at the binder — most of this
     * codebase's controllers still take an id as `String` and call `UUID.fromString` themselves,
     * which throws `IllegalArgumentException`. Client error → 400, not 500.
     *
     * This arm is wider than it looks: **any** `IllegalArgumentException` escaping any layer lands
     * here, including a `require(...)` that fails deep in a service, which is a server fault
     * answered as though the caller had mistyped something. That is a reason to keep moving parsing
     * to the binder — where [handleTypeMismatch] can tell the caller precisely what was wrong —
     * rather than a reason to widen this net further. Nothing should be added to it whose origin is
     * ambiguous; `DateTimeParseException` was considered and deliberately left out, because a date
     * that will not parse is a client error only when it came off the wire, and `LocalDate.parse`
     * is also called on values read back from the database.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Bad request: {}", ex.message)
        return respond(ErrorCode.VALIDATION_ERROR)
    }

    /**
     * A body the deserializer cannot read is the caller's mistake, not ours. Without this it fell
     * through to [handleUnexpected] and answered 500 `INTERNAL_ERROR` with an ERROR-level stack
     * trace — telling the caller the server had broken and inviting a retry that could never
     * succeed, while letting anyone fill the production log with ERROR noise by posting junk.
     *
     * The decoder's own message is passed through because it names the offending key, which is the
     * only thing that makes the 400 actionable ("unknown key 'code'" — the field is `otp`).
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Unreadable request body: {}", ex.mostSpecificCause.message)
        return respond(
            ErrorCode.VALIDATION_ERROR,
            ex.mostSpecificCause.message ?: ErrorCode.VALIDATION_ERROR.defaultMessage
        )
    }

    /**
     * The bug in #472: `DELETE` against a path that only answers `GET`/`PATCH` was reaching
     * [handleUnexpected] and coming back 500 "Beklenmeyen bir hata oluştu", logged at ERROR. The
     * mobile client could not distinguish that from a real server fault, so a not-yet-deployed
     * endpoint was reported to the user as a backend outage.
     *
     * The `Allow` header is not decoration: RFC 9110 §15.5.6 requires a 405 to carry it, and it is
     * what lets a client work out which verb it should have used.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Method not allowed: {} (supported: {})", ex.method, ex.supportedHttpMethods)
        val builder = ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus)
        ex.supportedHttpMethods?.takeIf { it.isNotEmpty() }?.let { builder.allow(*it.toTypedArray()) }
        return builder.body(envelope(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage))
    }

    /** Wrong `Content-Type` on the request — e.g. JSON posted to a multipart-only upload. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedContentType(
        ex: HttpMediaTypeNotSupportedException
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Unsupported Content-Type: {} (supported: {})", ex.contentType, ex.supportedMediaTypes)
        return respond(ErrorCode.UNSUPPORTED_CONTENT_TYPE)
    }

    /** An `Accept` header we cannot satisfy. Every endpoint here speaks JSON, so this is a typo. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleNotAcceptable(ex: HttpMediaTypeNotAcceptableException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Not acceptable: client asked for {}", ex.supportedMediaTypes)
        return respond(ErrorCode.NOT_ACCEPTABLE)
    }

    /**
     * A required query parameter, header or multipart part the caller left out. Real endpoints
     * depend on these — `GET /messages/since?timestamp=`, `GET /search?q=`,
     * `POST /media/upload` with a `file` part — and omitting one used to answer 500.
     *
     * `MissingPathVariableException` is deliberately **not** here even though it is a sibling of
     * `MissingServletRequestParameterException`: a path variable that the mapping declares but the
     * URI does not supply is our own routing bug, and Spring is right to call it a 500.
     */
    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MissingRequestHeaderException::class,
        MissingServletRequestPartException::class
    )
    fun handleMissingRequestValue(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Missing request value: {}", ex.message)
        return respond(ErrorCode.VALIDATION_ERROR, ex.message ?: ErrorCode.VALIDATION_ERROR.defaultMessage)
    }

    /**
     * Over `spring.servlet.multipart.max-file-size`. 413 with the existing `MEDIA_TOO_LARGE` code —
     * the client already knows how to show that, and telling someone their 200 MB video "caused an
     * unexpected server error" invites them to retry it.
     *
     * Only the size overflow is mapped. A `MultipartException` from a torn body is left to
     * [handleUnexpected] on purpose: it can also mean the server's temp directory is unwritable,
     * and blaming the client for that would bury a real fault.
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(ex: MaxUploadSizeExceededException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Upload rejected, over the size limit: {}", ex.message)
        return respond(ErrorCode.MEDIA_TOO_LARGE)
    }

    /**
     * An address that maps to nothing — a typo, a stale client, or a vulnerability scanner walking
     * `/wp-login.php`. Logged at DEBUG rather than WARN precisely because scanners are relentless:
     * promoting that traffic to WARN would recreate the problem #472 is about one level down.
     *
     * `NoResourceFoundException` is the one that actually fires. `spring.web.resources.add-mappings`
     * is left at its default, so `ResourceHttpRequestHandler` is mapped at the catch-all path and
     * claims every unmatched URL before the `DispatcherServlet` can conclude there is no handler.
     * `NoHandlerFoundException` is listed alongside it so that turning static-resource mapping off —
     * the usual hardening step for an API-only service — does not silently restore the 500s.
     */
    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFound(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.debug("No handler for request: {}", ex.message)
        return respond(ErrorCode.ENDPOINT_NOT_FOUND)
    }

    /**
     * Genuinely unexpected: an escaped bug, a dead dependency, a broken invariant. Keeps ERROR and
     * the full stack trace — that is the whole point of the arms above, so this line stays worth
     * reading and worth paging on.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unexpected error", ex)
        return respond(ErrorCode.INTERNAL_ERROR)
    }

    private fun respond(
        code: ErrorCode,
        message: String = code.defaultMessage
    ): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(code.httpStatus).body(envelope(code, message))

    private fun envelope(code: ErrorCode, message: String): ApiResponse<Nothing> =
        ApiResponse(
            error = ApiError(code = code.name, message = message),
            timestamp = Instant.now().toString()
        )
}

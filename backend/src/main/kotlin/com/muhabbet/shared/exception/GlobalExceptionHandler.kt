package com.muhabbet.shared.exception

import com.muhabbet.shared.dto.ApiError
import com.muhabbet.shared.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Business error: {} - {}", ex.errorCode, ex.message)
        return ResponseEntity
            .status(ex.errorCode.httpStatus)
            .body(
                ApiResponse(
                    error = ApiError(
                        code = ex.errorCode.name,
                        message = ex.message
                    ),
                    timestamp = Instant.now().toString()
                )
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation error: {}", message)
        return ResponseEntity
            .badRequest()
            .body(
                ApiResponse(
                    error = ApiError(
                        code = ErrorCode.VALIDATION_ERROR.name,
                        message = message
                    ),
                    timestamp = Instant.now().toString()
                )
            )
    }

    /**
     * Malformed inputs that Spring/JVM raise before they reach the service layer:
     * a bad UUID in a path variable (`MethodArgumentTypeMismatchException`) or in a request body
     * (`UUID.fromString` → `IllegalArgumentException`). These are client errors → 400, not 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class, IllegalArgumentException::class)
    fun handleBadRequest(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Bad request: {}", ex.message)
        return ResponseEntity
            .badRequest()
            .body(
                ApiResponse(
                    error = ApiError(
                        code = ErrorCode.VALIDATION_ERROR.name,
                        message = ErrorCode.VALIDATION_ERROR.defaultMessage
                    ),
                    timestamp = Instant.now().toString()
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unexpected error", ex)
        return ResponseEntity
            .internalServerError()
            .body(
                ApiResponse(
                    error = ApiError(
                        code = ErrorCode.INTERNAL_ERROR.name,
                        message = ErrorCode.INTERNAL_ERROR.defaultMessage
                    ),
                    timestamp = Instant.now().toString()
                )
            )
    }
}

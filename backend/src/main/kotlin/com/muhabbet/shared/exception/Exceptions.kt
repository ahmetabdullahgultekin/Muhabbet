package com.muhabbet.shared.exception

/**
 * Business exception — thrown by domain services, caught by GlobalExceptionHandler.
 *
 * [ErrorCode] is the shared wire catalogue (shared KMP module, same package); its HTTP status and
 * default message are attached by [spec] in this module.
 */
class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.defaultMessage,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

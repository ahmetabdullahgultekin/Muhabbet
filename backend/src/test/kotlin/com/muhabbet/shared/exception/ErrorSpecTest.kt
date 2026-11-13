package com.muhabbet.shared.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * #883: the catalogue moved to the shared module; the HTTP half stayed here. The `when` is exhaustive,
 * so the compiler already refuses a code without a status — these pin the few values whose exact
 * status is part of a client's behaviour.
 */
class ErrorSpecTest {

    @Test
    fun `should give every shared code a status and a non-blank default message`() {
        ErrorCode.entries.forEach { code ->
            assertTrue(code.defaultMessage.isNotBlank(), "$code has no default message")
            assertTrue(code.httpStatus.isError, "$code maps to ${code.httpStatus}")
        }
    }

    @Test
    fun `should answer an outdated client with 426`() {
        assertEquals(HttpStatus.UPGRADE_REQUIRED, ErrorCode.CLIENT_UPGRADE_REQUIRED.httpStatus)
    }

    @Test
    fun `should keep the statuses clients branch on unchanged by the move`() {
        // AUTH_OTP_INVALID is 401, which the app's Ktor auth plugin must not replay (#400); the 2FA lock
        // is 429 precisely so it is not mistaken for a wrong PIN (#566).
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_OTP_INVALID.httpStatus)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.AUTH_2FA_LOCKED.httpStatus)
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE.httpStatus)
    }
}

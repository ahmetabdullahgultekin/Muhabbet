package com.muhabbet.shared.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment

/**
 * The OTP mock paths log every code, and one of them returns it in the HTTP response. Either would
 * let anyone sign in as any number, so outside the dev and test profiles the server refuses to
 * start with them on — the same fail-closed stance as the JWT secret guard.
 */
class OtpModeGuardTest {

    private fun env(vararg profiles: String) = MockEnvironment().apply { setActiveProfiles(*profiles) }

    @Test
    fun `should refuse to start when the mock SMS provider is configured under the prod profile`() {
        assertThrows<IllegalStateException> {
            OtpModeGuard(OtpProperties(), SmsProperties(provider = "mock"), env("prod")).validate()
        }
    }

    @Test
    fun `should refuse to start when OTP mock mode is on and no profile is active`() {
        assertThrows<IllegalStateException> {
            OtpModeGuard(OtpProperties(mockEnabled = true), SmsProperties(provider = "twilio-verify"), env()).validate()
        }
    }

    @Test
    fun `should start when a real provider is configured under the prod profile`() {
        assertDoesNotThrow {
            OtpModeGuard(OtpProperties(), SmsProperties(provider = "twilio-verify"), env("prod")).validate()
        }
    }

    @Test
    fun `should start when the mock is used under the dev or test profile`() {
        assertDoesNotThrow {
            OtpModeGuard(OtpProperties(mockEnabled = true), SmsProperties(provider = "mock"), env("dev")).validate()
            OtpModeGuard(OtpProperties(mockEnabled = true), SmsProperties(provider = "mock"), env("test")).validate()
        }
    }
}

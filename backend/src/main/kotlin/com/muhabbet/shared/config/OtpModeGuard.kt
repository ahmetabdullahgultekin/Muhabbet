package com.muhabbet.shared.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Fail-closed startup guard for the OTP mock paths, in the spirit of `JwtProvider.validateSecret`.
 *
 * `muhabbet.sms.provider=mock` writes every code to the log, and `muhabbet.otp.mock-enabled=true`
 * returns it in the HTTP response, which lets anyone sign in as any number. Both are conveniences
 * for a developer's machine and for tests. Both used to be one missing environment variable away
 * from production — the provider even defaults to `mock` — so they are now refused at boot unless
 * the dev or test profile is active. A deployment that forgets to configure a real provider stops
 * with a clear message instead of starting with an open door.
 */
@Component
class OtpModeGuard(
    private val otpProperties: OtpProperties,
    private val smsProperties: SmsProperties,
    private val environment: Environment
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun validate() {
        val profiles = environment.activeProfiles.toSet()
        if (profiles.any { it in MOCK_PROFILES }) return

        val label = profiles.ifEmpty { setOf("default") }.joinToString(",")
        check(!smsProperties.provider.equals(MOCK_PROVIDER, ignoreCase = true)) {
            "muhabbet.sms.provider is 'mock' outside the dev/test profiles — refusing to start (profile=$label). " +
                "Set SMS_PROVIDER to a real provider."
        }
        check(!otpProperties.mockEnabled) {
            "muhabbet.otp.mock-enabled is true outside the dev/test profiles — refusing to start (profile=$label). " +
                "Unset OTP_MOCK_ENABLED."
        }
        log.info("OTP delivery mode validated (provider={}, profile={}).", smsProperties.provider, label)
    }

    private companion object {
        val MOCK_PROFILES = setOf("dev", "test")
        const val MOCK_PROVIDER = "mock"
    }
}

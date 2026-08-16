package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "muhabbet.otp")
data class OtpProperties(
    val length: Int = 6,
    val expirySeconds: Int = 300,
    val maxAttempts: Int = 5,
    val cooldownSeconds: Int = 60,
    val mockEnabled: Boolean = false,
    /**
     * Phone numbers whose OTP is generated locally and written to the log instead of being sent
     * through the SMS provider. Empty by default, so the feature does not exist unless configured.
     *
     * This exists because there is otherwise **no way to sign in on an emulator**: production uses
     * Twilio Verify, the code never exists on our side, and any number a tester invents receives no
     * SMS. Without it, every mobile change ships compiled-but-never-seen, which is how a release
     * went out with a crash on the camera button and another with a crash on swipe-to-reply.
     *
     * Deliberately **not** a fixed code per number. A constant would be a permanent password
     * guessable in about 500k attempts; a freshly generated code that only reaches the server log
     * means reading it requires server access, which is a much higher bar than guessing. Everything
     * else — cooldown, the attempt limit, IP rate limiting — is the ordinary path, unchanged.
     *
     * [com.muhabbet.auth.domain.service.AuthService] refuses to start unless every entry is in the
     * `+90500` range, which BTK has not allocated, so a real person's number cannot be listed here
     * by mistake.
     */
    val testNumbers: List<String> = emptyList()
)

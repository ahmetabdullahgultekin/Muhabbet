package com.muhabbet.auth.domain.model

/**
 * The tunables of the OTP sign-in, as one value (#894).
 *
 * They were six loose constructor parameters on the old `AuthService`, between twelve others.
 * Grouping them is what lets the OTP service's constructor say what it collaborates with rather
 * than list every number in `application.yml`.
 */
data class OtpSettings(
    val length: Int = 6,
    val expirySeconds: Int = 300,
    val cooldownSeconds: Int = 60,
    val maxAttempts: Int = 5,
    /** Returns the generated code in the response. Local development only. */
    val mockEnabled: Boolean = false,
    /**
     * Numbers that bypass the SMS provider: the code is generated here and written to the log.
     * See `OtpProperties.testNumbers` for why this exists and why it is not a fixed code.
     */
    val testNumbers: Set<String> = emptySet()
)

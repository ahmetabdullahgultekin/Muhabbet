package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The guess budget for the two-step PIN (#566).
 *
 * Configurable for the same reason the OTP's limits are: the right numbers are an operational
 * judgement, and the one thing that must never happen is someone lowering the ceiling by editing
 * code. Both are deliberately small — a second factor is worth what its guess rate is worth.
 */
@ConfigurationProperties(prefix = "muhabbet.two-step")
data class TwoStepProperties(
    /** Consecutive wrong PINs before the lockout starts. */
    val maxAttempts: Int = 5,
    /**
     * How long the lockout lasts. A window and not a permanent block: with no recovery flow, a
     * permanent lock on five typos would end the account. At 5 guesses per 15 minutes, exhausting a
     * six-digit space takes about six years — which is the actual protection here, since BCrypt only
     * slows an attacker who already has the database.
     */
    val lockSeconds: Long = 900
)

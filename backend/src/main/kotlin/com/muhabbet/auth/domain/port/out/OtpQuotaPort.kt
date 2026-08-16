package com.muhabbet.auth.domain.port.out

/**
 * Bounds how many verifications may be *started*, because each one costs money.
 *
 * `SMS_PROVIDER=twilio-verify` bills per verification started, and until #440 nothing capped the
 * total: ten requests per minute per IP is 14,400 billed verifications a day from one machine, with
 * no hourly or daily ceiling anywhere. The realistic attack is not guessing a code — the attempt
 * limit stops that — it is running up the bill until the provider cuts the account off, at which
 * point real users cannot log in either.
 *
 * Counters live outside the JVM so they survive a deploy and are shared across instances. The
 * per-IP throttle in `RateLimitFilter` is deliberately not this: it is in-process, resets on
 * restart, and is a throughput smoother rather than a budget.
 */
interface OtpQuotaPort {

    /**
     * Records one verification about to be started and reports whether it is within budget.
     *
     * Returns false when either ceiling is reached: the deployment-wide hourly limit, or the daily
     * limit for this one number. A refusal is the intended outcome — a deliberate stop is better
     * than an unexpected invoice or a provider cutoff nobody chose.
     */
    fun tryConsume(phoneNumber: String): Boolean
}

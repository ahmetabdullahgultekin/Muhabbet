package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.OtpRequest

interface OtpRepository {
    fun save(otpRequest: OtpRequest): OtpRequest
    fun findActiveByPhoneNumber(phoneNumber: String): OtpRequest?
    /**
     * Claims one verification attempt against [maxAttempts]. Returns false when the budget is spent.
     *
     * Deliberately not a read followed by an increment: the two together are not atomic, and
     * concurrent verifies interleave between them.
     */
    fun claimAttempt(otpRequest: OtpRequest, maxAttempts: Int): Boolean

    /**
     * Gives back an attempt claimed for a code that turned out to be **correct**.
     *
     * The budget exists to bound guesses at the SMS code. When two-step verification is on, the
     * right code is not the end of the sign-in: the client is told a PIN is needed and comes back
     * with the same six digits plus the PIN. Charging that round trip would spend the OTP budget on
     * something that was never a guess, and a user who mistyped the PIN twice would be told the
     * *code* had run out of attempts. That is #688's shape — one entered code costing two of five —
     * and the PIN has its own counter, which is the one that should govern here.
     *
     * Only ever called after the code has been compared and accepted, so it cannot refund a guess.
     */
    fun refundAttempt(otpRequest: OtpRequest)

    fun markVerified(otpRequest: OtpRequest)
}

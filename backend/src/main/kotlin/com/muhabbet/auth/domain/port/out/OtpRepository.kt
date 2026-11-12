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

    /**
     * Records that the external verifier approved this request's code, by storing a hash of it in
     * place of the `EXTERNALLY_VERIFIED` sentinel.
     *
     * An external verifier (Twilio Verify) consumes a verification when it approves it, so asking it
     * a second time about the same code answers "no". A correct code can still be followed by a
     * refusal that the client answers by resending it — a missing two-step PIN, a missing date of
     * birth (#751) — and without this the resend would fail as `AUTH_OTP_INVALID`. With it the
     * resend is compared against the stored hash, under the same expiry and attempt budget as the
     * local path.
     *
     * Must commit independently of the caller, which throws after calling it. Only replaces the
     * sentinel, so it can never overwrite a locally generated code's hash.
     */
    fun recordExternalApproval(otpRequest: OtpRequest, codeHash: String)

    fun markVerified(otpRequest: OtpRequest)
}

package com.muhabbet.auth.domain.port.out

/**
 * How one-time codes reach a phone — exactly one of two strategies, chosen at wiring time (#886).
 *
 * This replaces a pair of nullable constructor parameters (`otpSender`, `otpVerifier`) guarded by
 * two `require` calls and, at the point of use, a `!!`. The type now says what the guards said: a
 * deployment has a channel, and it is one kind or the other.
 */
sealed interface OtpChannel {
    /** We generate and hash the code; [sender] only delivers it (mock, Netgsm, Twilio SMS). */
    data class LocalCode(val sender: OtpSender) : OtpChannel

    /** The provider owns the code end to end — generation and checking (Twilio Verify). */
    data class ExternalVerifier(val verifier: OtpVerifier) : OtpChannel
}

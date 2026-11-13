package com.muhabbet.app.ui.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #782: the verification stage as a pure reducer over a wall-clock expiry.
 *
 * The bug these pin: the countdown was a `LaunchedEffect(Unit)` loop that ended at zero, and
 * Resend set the counter back to 300 with nothing left to decrement it — the label read "5:00"
 * forever and Resend never came back. Here time is a fake clock passed in as numbers, so "after a
 * resend the remaining time goes down on every tick" is a plain assertion.
 */
class OtpPhaseTest {

    private val t0 = 1_000_000L

    private fun OtpPhase.after(vararg events: OtpEvent): OtpPhase =
        events.fold(this) { phase, event -> reduceOtp(phase, event) }

    @Test
    fun should_countDownFromTheExpiryInstant_when_ticking() {
        val phase = OtpPhase.Entering(expiresAtMillis = t0 + 300_000)

        assertEquals(300, phase.remainingSeconds(t0))
        assertEquals(299, phase.remainingSeconds(t0 + 1_000))
        // Rounded up: the last partial second still reads 0:01, never a premature 0:00.
        assertEquals(1, phase.remainingSeconds(t0 + 299_500))
        assertEquals(0, phase.remainingSeconds(t0 + 300_000))
    }

    @Test
    fun should_expire_when_aTickReachesTheExpiryInstant() {
        val phase = OtpPhase.Entering(expiresAtMillis = t0 + 60_000)

        assertEquals(phase, phase.after(OtpEvent.Tick(t0 + 59_999)))
        assertEquals(OtpPhase.Expired, phase.after(OtpEvent.Tick(t0 + 60_000)))
    }

    @Test
    fun should_offerResendAgainAndCountDownAfterIt_when_resentTwice() {
        // The exact sequence that froze: expire, resend, expire again, resend again.
        var phase: OtpPhase = OtpPhase.Entering(expiresAtMillis = t0 + 60_000)
        phase = phase.after(OtpEvent.Tick(t0 + 60_000))
        assertTrue(phase.canResend)

        phase = phase.after(OtpEvent.ResendRequested)
        assertTrue(phase is OtpPhase.Resending)
        phase = phase.after(OtpEvent.CodeSent(nowMillis = t0 + 70_000, ttlSeconds = 300))
        assertEquals(OtpPhase.Entering(t0 + 370_000), phase)

        // Decreases on every tick after the resend — the frozen "5:00" is gone.
        val readings = (0..3).map { phase.remainingSeconds(t0 + 70_000 + it * 1_000L) }
        assertEquals(listOf(300, 299, 298, 297), readings)

        phase = phase.after(OtpEvent.Tick(t0 + 370_000))
        assertEquals(OtpPhase.Expired, phase)
        assertTrue(phase.canResend, "a second resend must be possible")
        phase = phase.after(OtpEvent.ResendRequested, OtpEvent.CodeSent(t0 + 400_000, 300))
        assertEquals(OtpPhase.Entering(t0 + 700_000), phase)
    }

    @Test
    fun should_returnToTheResendButton_when_theResendFails() {
        val failed = OtpPhase.Expired.after(OtpEvent.ResendRequested, OtpEvent.ResendFailed)
        assertEquals(OtpPhase.Expired, failed)

        val lockedFailed = OtpPhase.Locked.after(OtpEvent.ResendRequested, OtpEvent.ResendFailed)
        assertEquals(OtpPhase.Locked, lockedFailed)
    }

    @Test
    fun should_notOfferResend_when_theCodeIsStillValid() {
        val phase = OtpPhase.Entering(t0 + 60_000)
        assertFalse(phase.canResend)
        assertEquals(phase, phase.after(OtpEvent.ResendRequested))
    }

    @Test
    fun should_moveToTheTwoStepStageAndStopTheClock_when_aPinIsRequired() {
        val phase = OtpPhase.Entering(t0 + 60_000).after(otpEventForFailure("AUTH_2FA_PIN_REQUIRED")!!)
        assertEquals(OtpPhase.AwaitingTwoStep, phase)
        assertTrue(phase.acceptsSubmission)
        // A tick long past the code's expiry does not take the PIN step away.
        assertEquals(OtpPhase.AwaitingTwoStep, phase.after(OtpEvent.Tick(t0 + 10_000_000)))
    }

    @Test
    fun should_lockAndOfferResend_when_attemptsAreExhausted() {
        val phase = OtpPhase.Entering(t0 + 60_000).after(otpEventForFailure("AUTH_OTP_MAX_ATTEMPTS")!!)
        assertEquals(OtpPhase.Locked, phase)
        assertFalse(phase.acceptsSubmission)
        assertTrue(phase.canResend)
    }

    @Test
    fun should_expire_when_theServerSaysTheCodeExpiredBeforeTheClientClockDid() {
        val phase = OtpPhase.Entering(t0 + 60_000).after(otpEventForFailure("AUTH_OTP_EXPIRED")!!)
        assertEquals(OtpPhase.Expired, phase)
    }

    @Test
    fun should_leaveTheStageAlone_when_theFailureIsNotAboutTheCode() {
        assertEquals(null, otpEventForFailure("AUTH_OTP_INVALID"))
        assertEquals(null, otpEventForFailure(null))
    }

    @Test
    fun should_wakeOnTheSecondBoundary_when_scheduling() {
        val phase = OtpPhase.Entering(t0 + 10_000)
        assertEquals(1_000, phase.millisUntilNextTick(t0))
        assertEquals(250, phase.millisUntilNextTick(t0 + 750))
        assertEquals(0, phase.millisUntilNextTick(t0 + 10_000))
    }

    @Test
    fun should_preserveStageAcrossSaveAndRestore_when_rotated() {
        listOf(OtpPhase.Entering(t0), OtpPhase.AwaitingTwoStep, OtpPhase.Expired, OtpPhase.Locked).forEach {
            assertEquals(it, decodeOtpPhase(encodeOtpPhase(it)))
        }
    }

    @Test
    fun should_restoreTheFallbackStage_when_savedMidResend() {
        // The resend request died with the old composition; restoring "Resending" would spin forever.
        val saved = encodeOtpPhase(OtpPhase.Resending(after = OtpPhase.Expired))
        assertEquals(OtpPhase.Expired, decodeOtpPhase(saved))
    }

    @Test
    fun should_restoreAsExpired_when_theSavedValueIsUnreadable() {
        assertEquals(OtpPhase.Expired, decodeOtpPhase("E:not-a-number"))
        assertEquals(OtpPhase.Expired, decodeOtpPhase("garbage"))
    }

    @Test
    fun should_computeExpiryFromTheServerTtl() {
        assertEquals(t0 + 300_000, codeExpiresAt(ttlSeconds = 300, nowMillis = t0))
    }
}

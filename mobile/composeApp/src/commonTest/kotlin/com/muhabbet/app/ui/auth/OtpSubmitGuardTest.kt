package com.muhabbet.app.ui.auth

import com.muhabbet.app.data.remote.ApiException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule that decides whether an entered code may be sent (#400).
 *
 * The screen has two submit paths — the field fires `onFilled` on the sixth digit, the button fires
 * on a tap — and one entered code must produce at most one request, because the server claims an
 * attempt before it compares anything.
 *
 * These tests pin the *rule*, not Compose's dispatch: `shouldSubmitOtp` is read inside the event
 * handler, where a snapshot write from the first caller is already visible, rather than through the
 * button's `enabled`, which is only re-evaluated on recomposition. A composed double-tap is not
 * reproducible in a JVM unit test and was not driven on a device — see the PR.
 */
class OtpSubmitGuardTest {

    private val code = "342156"

    @Test
    fun aCompleteCodeThatNothingIsHoldingIsSent() {
        assertTrue(shouldSubmitOtp(code, inFlight = false, alreadySubmitted = null))
    }

    @Test
    fun anIncompleteCodeIsNotSent() {
        // The field calls back on every keystroke, so this is the common case by five to one.
        assertFalse(shouldSubmitOtp("34215", inFlight = false, alreadySubmitted = null))
        assertFalse(shouldSubmitOtp("", inFlight = false, alreadySubmitted = null))
    }

    @Test
    fun aSecondCallerInTheSameFrameIsRefused() {
        // The auto-submit has fired and set both flags; the tap that lands before the next
        // recomposition sees them. This is the pair of dispatches that cost two of five attempts.
        assertFalse(shouldSubmitOtp(code, inFlight = true, alreadySubmitted = code))
    }

    @Test
    fun aTapWhileTheRequestIsStillOutIsRefused() {
        assertFalse(shouldSubmitOtp(code, inFlight = true, alreadySubmitted = null))
    }

    @Test
    fun theSameCodeIsNotSentTwiceAfterTheRequestReturns() {
        // `inFlight` is already false here: the response came back and was a rejection. Without the
        // code latch this is exactly the second request #400 reported.
        assertFalse(shouldSubmitOtp(code, inFlight = false, alreadySubmitted = code))
    }

    @Test
    fun adifferentCodeIsSentEvenThoughOneWasJustRefused() {
        // The user corrected the typo. The latch must not outlive the code it was taken out on.
        assertTrue(shouldSubmitOtp("100000", inFlight = false, alreadySubmitted = code))
    }

    @Test
    fun theSameCodeIsSentAgainOnceTheFieldHasBeenEdited() {
        // Retyping the same six digits passes through shorter values, and the screen clears the
        // latch on every value change — so a genuine re-entry is a fresh attempt, with no timer
        // anywhere in the decision.
        assertTrue(shouldSubmitOtp(code, inFlight = false, alreadySubmitted = null))
    }

    @Test
    fun aRejectedCodeCountsAsAnAttemptAndStaysLatched() {
        assertTrue(consumedAnAttempt(ApiException(401, "AUTH_OTP_INVALID", "Geçersiz doğrulama kodu")))
        assertTrue(consumedAnAttempt(ApiException(401, "AUTH_OTP_MAX_ATTEMPTS", "Maksimum deneme sayısı aşıldı")))
    }

    @Test
    fun aFailureThatNeverReachedTheCounterReleasesTheLatch() {
        // None of these spent an attempt, so refusing to resend the same digits would strand the
        // user on a dead Verify button holding a code that was never found wrong.
        assertFalse(consumedAnAttempt(ApiException(502, "HTTP_502", "Bad Gateway")))
        assertFalse(consumedAnAttempt(ApiException(500, "MEDIA_UPLOAD_FAILED", "x")))
        assertFalse(consumedAnAttempt(IllegalStateException("Firebase verifyCode failed")))
    }
}

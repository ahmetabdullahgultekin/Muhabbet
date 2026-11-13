package com.muhabbet.app.ui.auth

import androidx.compose.runtime.saveable.Saver
import kotlin.time.Clock

/**
 * Where the verification step stands, as one value instead of the flags it used to be (#782).
 *
 * The screen carried `countdown`, `twoStepRequired` and `isResending` as three independent
 * `remember`ed vars, plus a `LaunchedEffect(Unit)` that decremented the countdown once a second and
 * **finished for good** when it reached zero. Resend set `countdown = 300` on a counter nothing was
 * decrementing any more, so the label read "5:00" forever and the expired branch — the only place
 * the Resend button lived — never came back. A second resend was impossible.
 *
 * Two changes make that class of bug unrepresentable rather than patched:
 *
 * - **Time is derived, not counted.** [Entering] holds the instant the code expires, and the
 *   remaining time is `expiresAt - now` ([remainingSeconds]). A resend produces a new instant, and
 *   the ticker is keyed on it, so there is no counter to forget to restart. It also survives
 *   rotation and process death correctly: a wall-clock instant restored a minute later is a minute
 *   closer to expiry, where a restored counter would have handed the user a minute back.
 * - **The stage is a sealed state with a pure [reduceOtp].** Every transition is a line in one
 *   `when`, and each is unit-tested without Compose (ADR 0012).
 */
sealed interface OtpPhase {

    /** A code is outstanding and the clock is running. */
    data class Entering(val expiresAtMillis: Long) : OtpPhase

    /**
     * The server accepted the code and the account has a second factor (#566). The code's clock no
     * longer applies: the server has already judged the code, and if the PIN step itself takes too
     * long the server says so.
     */
    data object AwaitingTwoStep : OtpPhase

    /** The code ran out before it was used. Only a new code helps; Resend is offered. */
    data object Expired : OtpPhase

    /**
     * Every attempt on this code has been spent (`AUTH_OTP_MAX_ATTEMPTS`). Like [Expired] the only
     * way forward is a new code — but the user must be told why, so it is a state of its own rather
     * than an early expiry.
     */
    data object Locked : OtpPhase

    /**
     * A new code has been requested and has not arrived. [after] is where a failed request leaves
     * the user, so a refused resend (cooldown, no network) returns them to the same Resend button
     * rather than to a dead end.
     */
    data class Resending(val after: OtpPhase) : OtpPhase
}

sealed interface OtpEvent {
    /** The clock moved. Drives [OtpPhase.Entering] to [OtpPhase.Expired] at the expiry instant. */
    data class Tick(val nowMillis: Long) : OtpEvent

    data object ResendRequested : OtpEvent

    /** A new code was sent at [nowMillis] and is valid for [ttlSeconds]. */
    data class CodeSent(val nowMillis: Long, val ttlSeconds: Int) : OtpEvent

    data object ResendFailed : OtpEvent

    /** The server answered `AUTH_2FA_PIN_REQUIRED`: the code was right, a PIN is needed. */
    data object TwoStepRequired : OtpEvent

    /** The server answered `AUTH_OTP_MAX_ATTEMPTS`. */
    data object AttemptsExhausted : OtpEvent

    /**
     * The server answered `AUTH_OTP_EXPIRED`. The client clock is an estimate of the server's; when
     * they disagree the server wins, and the user is offered Resend instead of a Verify button that
     * can only fail again.
     */
    data object ServerExpired : OtpEvent
}

/**
 * The one place the verification stage changes. Pure: no clock is read here — time arrives in the
 * events — so every transition is testable with plain numbers.
 *
 * An event that does not apply to the current stage leaves it unchanged rather than throwing: a
 * tick that lands during [OtpPhase.AwaitingTwoStep] or a second Resend tap that races the first are
 * ordinary, and the right answer to both is "nothing happens".
 */
fun reduceOtp(phase: OtpPhase, event: OtpEvent): OtpPhase = when (event) {
    is OtpEvent.Tick -> when {
        phase is OtpPhase.Entering && event.nowMillis >= phase.expiresAtMillis -> OtpPhase.Expired
        else -> phase
    }

    OtpEvent.ResendRequested -> if (phase.canResend) OtpPhase.Resending(after = phase) else phase

    is OtpEvent.CodeSent -> when (phase) {
        is OtpPhase.Resending -> OtpPhase.Entering(event.nowMillis + event.ttlSeconds * MILLIS_PER_SECOND)
        else -> phase
    }

    OtpEvent.ResendFailed -> if (phase is OtpPhase.Resending) phase.after else phase

    // Applies from Entering and also from Expired: the server may judge a code a moment after the
    // client clock ran out, and its answer outranks the local estimate.
    OtpEvent.TwoStepRequired -> when (phase) {
        is OtpPhase.Entering, OtpPhase.Expired -> OtpPhase.AwaitingTwoStep
        else -> phase
    }

    OtpEvent.AttemptsExhausted -> when (phase) {
        is OtpPhase.Entering, OtpPhase.AwaitingTwoStep -> OtpPhase.Locked
        else -> phase
    }

    OtpEvent.ServerExpired -> when (phase) {
        is OtpPhase.Entering, OtpPhase.AwaitingTwoStep -> OtpPhase.Expired
        else -> phase
    }
}

/** The stage a rejected verification moves to, from the server's error code; null if none. */
fun otpEventForFailure(code: String?): OtpEvent? = when (code) {
    "AUTH_2FA_PIN_REQUIRED" -> OtpEvent.TwoStepRequired
    "AUTH_OTP_MAX_ATTEMPTS" -> OtpEvent.AttemptsExhausted
    "AUTH_OTP_EXPIRED" -> OtpEvent.ServerExpired
    else -> null
}

/** Resend is offered exactly when the outstanding code can no longer be used. */
val OtpPhase.canResend: Boolean
    get() = this == OtpPhase.Expired || this == OtpPhase.Locked

/** Whether Verify may be pressed at all in this stage (the per-attempt latch is separate). */
val OtpPhase.acceptsSubmission: Boolean
    get() = this is OtpPhase.Entering || this == OtpPhase.AwaitingTwoStep

val OtpPhase.isTwoStep: Boolean
    get() = this == OtpPhase.AwaitingTwoStep

/**
 * Whole seconds left on the outstanding code at [nowMillis], rounded **up** so the label reads
 * "0:01" for the last partial second and reaches "0:00" exactly when [reduceOtp] declares expiry.
 * Zero in every stage that has no running clock.
 */
fun OtpPhase.remainingSeconds(nowMillis: Long): Int {
    if (this !is OtpPhase.Entering) return 0
    val remaining = expiresAtMillis - nowMillis
    if (remaining <= 0) return 0
    return ((remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
}

/**
 * Milliseconds from [nowMillis] until the displayed second changes, so the ticker wakes exactly on
 * the boundary instead of drifting by a whole `delay(1000)` per tick.
 */
fun OtpPhase.millisUntilNextTick(nowMillis: Long): Long {
    if (this !is OtpPhase.Entering) return MILLIS_PER_SECOND
    val remaining = expiresAtMillis - nowMillis
    if (remaining <= 0) return 0
    val intoSecond = remaining % MILLIS_PER_SECOND
    return if (intoSecond == 0L) MILLIS_PER_SECOND else intoSecond
}

/**
 * Saves the stage across rotation and process death (#782 — rotation used to drop the two-step
 * stage and restart the clock).
 *
 * [OtpPhase.Resending] is saved as the stage it would fall back to. The request it describes ran in
 * the old composition's scope, which is gone once the activity is recreated, so restoring
 * "Resending" would show a spinner for a request nobody is waiting on — the screen would be stuck.
 */
val OtpPhaseSaver: Saver<OtpPhase, String> = Saver(
    save = { encodeOtpPhase(it) },
    restore = { decodeOtpPhase(it) }
)

internal fun encodeOtpPhase(phase: OtpPhase): String = when (phase) {
    is OtpPhase.Entering -> "E:${phase.expiresAtMillis}"
    OtpPhase.AwaitingTwoStep -> "T"
    OtpPhase.Expired -> "X"
    OtpPhase.Locked -> "L"
    is OtpPhase.Resending -> encodeOtpPhase(phase.after)
}

/** An unreadable value restores as [OtpPhase.Expired]: offering Resend is always safe. */
internal fun decodeOtpPhase(saved: String): OtpPhase = when {
    saved.startsWith("E:") -> saved.removePrefix("E:").toLongOrNull()
        ?.let { OtpPhase.Entering(it) } ?: OtpPhase.Expired
    saved == "T" -> OtpPhase.AwaitingTwoStep
    saved == "L" -> OtpPhase.Locked
    else -> OtpPhase.Expired
}

/**
 * The wall-clock instant a code sent now with [ttlSeconds] stops working — taken at the moment the
 * server answered, so the OTP screen's clock starts from the server's TTL and not from whenever the
 * screen happened to compose.
 */
fun codeExpiresAt(ttlSeconds: Int, nowMillis: Long = Clock.System.now().toEpochMilliseconds()): Long =
    nowMillis + ttlSeconds * MILLIS_PER_SECOND

/**
 * How long a backend code lives when the caller did not say (`AuthService` issues 300 s). Used only
 * when the screen is reached without the server's own `ttlSeconds`.
 */
internal const val BackendOtpTtlSeconds = 300

/**
 * Firebase's auto-retrieval window. Firebase does not report the code's own lifetime, so the screen
 * uses the window it asked for in `PhoneAuthOptions.setTimeout` — after that, Resend is offered.
 */
internal const val FirebaseOtpTtlSeconds = 60

private const val MILLIS_PER_SECOND = 1000L

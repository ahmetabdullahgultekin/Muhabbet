package com.muhabbet.app.navigation

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Where a person is in signing in (#819), as domain state rather than navigation history.
 *
 * The flow used to be a push stack, and the stack *was* the state: profile setup was pushed on top
 * of a code that had already been redeemed, so back returned to an OTP screen whose code no longer
 * worked while a valid session was already stored; and whether onboarding had finished was not
 * recorded anywhere, so a cold start during profile setup opened the app as a nameless account. Here
 * the screens are derived from the state ([AuthComponent.stackFor]), the profile step is forward-only,
 * and the state is what gets saved.
 *
 * Verification in flight is not a state of its own here: it belongs to the code screen, which owns
 * the request, the two-step PIN and the per-attempt guard (`shouldSubmitOtp`), and it ends in
 * [AuthFlowEvent.Verified] or stays on [CodeSent].
 */
@Serializable
sealed interface AuthFlowState {

    @Serializable
    data object EnterPhone : AuthFlowState

    /**
     * A code is on its way to [phoneNumber].
     *
     * @param firebaseVerificationId Firebase's handle for the SMS it sent, or null when the backend
     *   sent the code. Not a secret on its own — it is useless without the code — and it must survive
     *   process death, or a user who switched to their SMS app could not finish.
     * @param sentAtEpochMs when the code was (re)sent, so the expiry countdown survives a rotation or
     *   a process kill instead of restarting from full every time the screen is rebuilt.
     * @param devCode the code itself, which the backend returns only in mock/dev mode so a debug
     *   build can prefill it. `@Transient`: a one-time code is never written to saved state (#819).
     */
    @Serializable
    data class CodeSent(
        val phoneNumber: String,
        val firebaseVerificationId: String?,
        val sentAtEpochMs: Long,
        @Transient val devCode: String? = null
    ) : AuthFlowState

    /** Verified as a brand-new account; it must choose a name before the app opens. */
    @Serializable
    data object NeedsProfile : AuthFlowState

    /** Signed in and onboarded. The session takes it from here. */
    @Serializable
    data object Complete : AuthFlowState
}

sealed interface AuthFlowEvent {
    data class CodeRequested(
        val phoneNumber: String,
        val devCode: String?,
        val firebaseVerificationId: String?,
        val nowEpochMs: Long
    ) : AuthFlowEvent

    /** The code screen asked the backend for a fresh code and got one. */
    data class CodeResent(val devCode: String?, val nowEpochMs: Long) : AuthFlowEvent

    /** Back, from whichever step is showing. */
    data object Back : AuthFlowEvent

    /** The server accepted the code (or Firebase verified the number on its own). */
    data class Verified(val isNewUser: Boolean) : AuthFlowEvent

    data object ProfileSaved : AuthFlowEvent
}

/** Work the owner performs for a transition. */
sealed interface AuthFlowEffect {
    /** Tell the session onboarding is finished, which is what opens the app. */
    data object FinishOnboarding : AuthFlowEffect
}

data class AuthFlowTransition(
    val state: AuthFlowState,
    val effect: AuthFlowEffect? = null
)

/**
 * The sign-in flow's transition function: pure and total (ADR 0012). Every pair not listed leaves
 * the state as it is — notably [AuthFlowEvent.Back] on [AuthFlowState.NeedsProfile], which is what
 * makes the profile step forward-only.
 */
fun AuthFlowState.on(event: AuthFlowEvent): AuthFlowTransition = when (event) {
    is AuthFlowEvent.CodeRequested -> when (this) {
        AuthFlowState.EnterPhone -> AuthFlowTransition(
            AuthFlowState.CodeSent(
                phoneNumber = event.phoneNumber,
                firebaseVerificationId = event.firebaseVerificationId,
                sentAtEpochMs = event.nowEpochMs,
                devCode = event.devCode
            )
        )
        else -> AuthFlowTransition(this)
    }
    is AuthFlowEvent.CodeResent -> when (this) {
        is AuthFlowState.CodeSent -> AuthFlowTransition(
            copy(sentAtEpochMs = event.nowEpochMs, devCode = event.devCode)
        )
        else -> AuthFlowTransition(this)
    }
    AuthFlowEvent.Back -> when (this) {
        is AuthFlowState.CodeSent -> AuthFlowTransition(AuthFlowState.EnterPhone)
        else -> AuthFlowTransition(this)
    }
    is AuthFlowEvent.Verified -> when (this) {
        AuthFlowState.EnterPhone,
        is AuthFlowState.CodeSent -> AuthFlowTransition(
            if (event.isNewUser) AuthFlowState.NeedsProfile else AuthFlowState.Complete
        )
        else -> AuthFlowTransition(this)
    }
    AuthFlowEvent.ProfileSaved -> when (this) {
        AuthFlowState.NeedsProfile -> AuthFlowTransition(
            AuthFlowState.Complete,
            AuthFlowEffect.FinishOnboarding
        )
        else -> AuthFlowTransition(this)
    }
}

/** How long a code stays valid: Firebase's SMS codes for a minute, the backend's for five. */
fun AuthFlowState.CodeSent.lifetimeSeconds(): Int =
    if (firebaseVerificationId != null) FIREBASE_CODE_LIFETIME_SECONDS else BACKEND_CODE_LIFETIME_SECONDS

/** Seconds left on the code at [nowEpochMs], never negative. */
fun AuthFlowState.CodeSent.secondsRemaining(nowEpochMs: Long): Int {
    val elapsedSeconds = ((nowEpochMs - sentAtEpochMs).coerceAtLeast(0) / MILLIS_PER_SECOND).toInt()
    return (lifetimeSeconds() - elapsedSeconds).coerceAtLeast(0)
}

const val FIREBASE_CODE_LIFETIME_SECONDS = 60
const val BACKEND_CODE_LIFETIME_SECONDS = 300
private const val MILLIS_PER_SECOND = 1000L

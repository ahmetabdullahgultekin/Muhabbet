package com.muhabbet.app.session

/**
 * Who, if anyone, this process is acting for — the one answer every other part of the app asks
 * (#813).
 *
 * "Signed in" used to be derived from which navigation child was on screen, and `TokenStorage` was a
 * passive store that three different places cleared without telling anyone. A rejected refresh
 * therefore emptied the token store while the conversation list stayed up, every request failed with
 * a generic toast, and the socket polled for a token every two seconds until the app was killed.
 * This sealed hierarchy is the lifecycle those booleans could not represent, and [on] is the only way
 * it moves (ADR 0012).
 *
 * Each state carries only what is valid in it. There is no `deviceId` here because nothing that
 * reacts to the session needs one; the token store still holds it for the requests that do.
 */
sealed interface SessionState {

    /** Nobody is signed in, and nobody was pushed out: a fresh install, or a deliberate logout. */
    data object SignedOut : SessionState

    /**
     * The server has issued tokens for a brand-new account that has not chosen a name yet (#819).
     *
     * Authenticated but not onboarded, which the old `isLoggedIn()` could not tell apart: a process
     * restart here opened the conversation list as a nameless account and others saw a bare phone
     * number. The socket, push registration and the rest of the session's side effects stay off
     * until [SignedIn], exactly as they were while this step was an Auth screen.
     */
    data class NeedsProfile(val userId: String) : SessionState

    /** A fully onboarded account is in use. */
    data class SignedIn(val userId: String) : SessionState

    /**
     * [SignedIn], while the access token is being renewed.
     *
     * Its own state so a refresh is observable and so a rejection can only come from somewhere a
     * refresh was actually in flight or the session was live — but deliberately **still a session**
     * for everything that reacts to one ([activeUserId]): tearing the socket down for the second a
     * refresh takes would turn every token renewal into a visible disconnect.
     */
    data class Refreshing(val userId: String) : SessionState

    /**
     * The server stopped honouring the session — its refresh token was rejected or revoked.
     *
     * Distinct from [SignedOut] so the login screen can say *why* the user is looking at it
     * (#813: "your session expired") instead of presenting an unexplained logout.
     */
    data class Expired(val reason: SessionEndReason) : SessionState
}

/** Why a session ended without the user asking it to. */
enum class SessionEndReason {
    /** `/auth/token/refresh` answered 401/403, or accepted the call and returned no tokens. */
    REFRESH_REJECTED
}

/** Everything that can move a [SessionState]. User intents and network outcomes alike. */
sealed interface SessionEvent {

    /**
     * OTP (or Firebase) verification succeeded and tokens were stored.
     *
     * @param needsProfile the server said the account is new, so onboarding is not finished.
     * @param localDataOwner the account the on-device caches currently belong to, read from storage
     *   by the owner of this machine; null on a fresh install or after a wipe. Carried in the event so
     *   the decision to wipe stays inside this pure function.
     */
    data class Authenticated(
        val userId: String,
        val needsProfile: Boolean,
        val localDataOwner: String?
    ) : SessionEvent

    /** The new account saved its display name. */
    data object ProfileCompleted : SessionEvent

    /** The HTTP client is about to call `/auth/token/refresh`. */
    data object RefreshStarted : SessionEvent

    /** The refresh returned new tokens. */
    data object RefreshSucceeded : SessionEvent

    /** The refresh did not complete for a reason that says nothing about the session (no network, 5xx). */
    data object RefreshFailedTransiently : SessionEvent

    /** The server refused the refresh token. The session is over. */
    data object RefreshRejected : SessionEvent

    /** The user chose to log out. */
    data object LoggedOut : SessionEvent

    /** The server confirmed the account was deleted. */
    data object AccountDeleted : SessionEvent
}

/**
 * Work a transition asks its owner to do. Data, not calls, so [on] stays pure and every test can
 * assert exactly what a transition would have done (ADR 0012).
 */
sealed interface SessionEffect {

    /** Forget the access and refresh tokens and everything else `TokenStorage.clear()` covers. */
    data object ClearCredentials : SessionEffect

    /**
     * Delete the account's on-device data: cached conversations, cached messages and the queue of
     * unsent messages. Without this the next account to sign in on the device saw the previous
     * one's chat list, and its socket drained the previous one's unsent messages under its own
     * name (#829).
     */
    data object WipeAccountData : SessionEffect

    /** Record that the on-device data now belongs to [userId]. */
    data class ClaimAccountData(val userId: String) : SessionEffect

    /** Onboarding finished; stop resuming at the profile step on the next launch. */
    data object ClearProfilePending : SessionEffect

    /** Open the per-account dependency scope (#829). */
    data class OpenAccountScope(val userId: String) : SessionEffect

    /** Close it, discarding every per-account holder created inside it. */
    data object CloseAccountScope : SessionEffect
}

/** A new state and the effects that must run to make the world match it, in order. */
data class SessionTransition(
    val state: SessionState,
    val effects: List<SessionEffect> = emptyList()
)

/**
 * The account a session is running for, or null when nothing session-scoped should be running.
 *
 * [SessionState.Refreshing] counts, [SessionState.NeedsProfile] does not: the socket, the receipt
 * pump and push registration have always started only once onboarding finished.
 */
val SessionState.activeUserId: String?
    get() = when (this) {
        is SessionState.SignedIn -> userId
        is SessionState.Refreshing -> userId
        is SessionState.NeedsProfile,
        is SessionState.Expired,
        SessionState.SignedOut -> null
    }

/** The account that holds credentials in this state, whether or not it has finished onboarding. */
val SessionState.authenticatedUserId: String?
    get() = when (this) {
        is SessionState.NeedsProfile -> userId
        else -> activeUserId
    }

/**
 * The state a process starts in, from what the token store holds.
 *
 * Pure, so the cold-start rule — tokens plus an unfinished profile resume at the profile step, not
 * in the app (#819) — is tested directly rather than through a restart.
 */
fun initialSessionState(
    accessToken: String?,
    userId: String?,
    profileSetupPending: Boolean
): SessionState = when {
    accessToken == null || userId == null -> SessionState.SignedOut
    profileSetupPending -> SessionState.NeedsProfile(userId)
    else -> SessionState.SignedIn(userId)
}

/**
 * The session's transition function: pure and total (ADR 0012). A pair with no meaning — a refresh
 * outcome with no session, a second verification while signed in — returns the state unchanged and
 * asks for nothing, rather than throwing.
 */
fun SessionState.on(event: SessionEvent): SessionTransition = when (event) {
    is SessionEvent.Authenticated -> onAuthenticated(event)
    SessionEvent.ProfileCompleted -> when (this) {
        is SessionState.NeedsProfile -> SessionTransition(
            SessionState.SignedIn(userId),
            listOf(SessionEffect.ClearProfilePending, SessionEffect.OpenAccountScope(userId))
        )
        else -> unchanged()
    }
    SessionEvent.RefreshStarted -> when (this) {
        is SessionState.SignedIn -> SessionTransition(SessionState.Refreshing(userId))
        else -> unchanged()
    }
    SessionEvent.RefreshSucceeded,
    SessionEvent.RefreshFailedTransiently -> when (this) {
        is SessionState.Refreshing -> SessionTransition(SessionState.SignedIn(userId))
        else -> unchanged()
    }
    SessionEvent.RefreshRejected -> when (this) {
        is SessionState.SignedIn,
        is SessionState.Refreshing,
        is SessionState.NeedsProfile -> SessionTransition(
            SessionState.Expired(SessionEndReason.REFRESH_REJECTED),
            // The account's on-device data is deliberately kept: the person most likely to sign in
            // next is the same one, and their unsent messages are still theirs. If someone else
            // signs in instead, Authenticated wipes it because the owner no longer matches.
            listOf(SessionEffect.ClearCredentials, SessionEffect.CloseAccountScope)
        )
        else -> unchanged()
    }
    SessionEvent.LoggedOut,
    SessionEvent.AccountDeleted -> when (this) {
        SessionState.SignedOut -> unchanged()
        is SessionState.Expired -> SessionTransition(
            SessionState.SignedOut,
            listOf(SessionEffect.WipeAccountData)
        )
        is SessionState.SignedIn,
        is SessionState.Refreshing,
        is SessionState.NeedsProfile -> SessionTransition(
            SessionState.SignedOut,
            listOf(
                SessionEffect.ClearCredentials,
                SessionEffect.CloseAccountScope,
                SessionEffect.WipeAccountData
            )
        )
    }
}

private fun SessionState.onAuthenticated(event: SessionEvent.Authenticated): SessionTransition {
    if (this !is SessionState.SignedOut && this !is SessionState.Expired) return unchanged()
    val effects = buildList {
        if (event.localDataOwner != event.userId) {
            add(SessionEffect.WipeAccountData)
            add(SessionEffect.ClaimAccountData(event.userId))
        }
        if (!event.needsProfile) add(SessionEffect.OpenAccountScope(event.userId))
    }
    val next = if (event.needsProfile) {
        SessionState.NeedsProfile(event.userId)
    } else {
        SessionState.SignedIn(event.userId)
    }
    return SessionTransition(next, effects)
}

private fun SessionState.unchanged() = SessionTransition(this)

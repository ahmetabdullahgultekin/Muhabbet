package com.muhabbet.app.session

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.util.Log
import com.muhabbet.shared.dto.AuthTokenResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one owner of the session lifecycle (#813): it holds [SessionState], runs [SessionState.on] for
 * every event, and is the only code that executes the resulting [SessionEffect]s.
 *
 * Before this, three places cleared the token store on their own — the HTTP client on a rejected
 * refresh, logout, and account deletion — and none of them told anything else. Now each reports an
 * event here, and everything that cares (the root navigation, the socket, the per-account holders)
 * observes [state]. An app-scoped Koin single, because the session is a fact about the process.
 *
 * Also the HTTP client's [RefreshOutcomeListener], which is how a rejected refresh becomes
 * [SessionState.Expired] instead of a silent `clear()`.
 *
 * Thread-safety: events arrive from the main thread and, for refresh outcomes, from the HTTP
 * client's dispatcher. A transition is published with compare-and-set, so two concurrent events can
 * never both apply to the same starting state, and the effects of a transition run once, by the
 * caller that won it.
 */
class SessionManager(
    private val tokenStorage: TokenStorage,
    private val accountData: AccountDataStore,
    private val accountScope: AccountScope
) : RefreshOutcomeListener {

    private val _state = MutableStateFlow(
        initialSessionState(
            accessToken = tokenStorage.getAccessToken(),
            userId = tokenStorage.getUserId(),
            profileSetupPending = tokenStorage.isProfileSetupPending()
        )
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        // A process that starts signed in has never passed through Authenticated, so the two
        // effects that transition would have run are run here instead.
        _state.value.activeUserId?.let { userId ->
            // An install upgraded from a build without ownership tracking holds the signed-in
            // account's data with no owner recorded. Claim it rather than wipe it: it is theirs.
            if (tokenStorage.getLocalDataOwner() == null) tokenStorage.setLocalDataOwner(userId)
            accountScope.open(userId)
        }
    }

    /**
     * Stores the tokens a verification returned and moves the session forward.
     *
     * The profile-pending flag is written **before** the tokens. A process that dies between the two
     * writes therefore wakes either signed out or at the profile step — never in the app as a
     * nameless account, which is the order that produced #819.
     */
    fun signIn(
        accessToken: String,
        refreshToken: String,
        userId: String,
        deviceId: String,
        isNewUser: Boolean
    ) {
        tokenStorage.setProfileSetupPending(isNewUser)
        tokenStorage.saveTokens(accessToken, refreshToken, userId, deviceId)
        dispatch(
            SessionEvent.Authenticated(
                userId = userId,
                needsProfile = isNewUser,
                localDataOwner = tokenStorage.getLocalDataOwner()
            )
        )
    }

    /** The new account saved its display name; onboarding is over. */
    fun completeProfile() = dispatch(SessionEvent.ProfileCompleted)

    /** The user asked to leave. */
    fun logout() = dispatch(SessionEvent.LoggedOut)

    /** The server confirmed the account no longer exists. */
    fun accountDeleted() = dispatch(SessionEvent.AccountDeleted)

    override fun onRefreshStarted() = dispatch(SessionEvent.RefreshStarted)
    override fun onRefreshSucceeded() = dispatch(SessionEvent.RefreshSucceeded)
    override fun onRefreshFailedTransiently() = dispatch(SessionEvent.RefreshFailedTransiently)
    override fun onRefreshRejected() = dispatch(SessionEvent.RefreshRejected)

    private fun dispatch(event: SessionEvent) {
        while (true) {
            val current = _state.value
            val transition = current.on(event)
            if (transition.state == current && transition.effects.isEmpty()) return
            if (_state.compareAndSet(current, transition.state)) {
                transition.effects.forEach(::execute)
                return
            }
        }
    }

    private fun execute(effect: SessionEffect) {
        try {
            when (effect) {
                SessionEffect.ClearCredentials -> tokenStorage.clear()
                SessionEffect.WipeAccountData -> {
                    accountData.wipe()
                    tokenStorage.setLocalDataOwner(null)
                }
                is SessionEffect.ClaimAccountData -> tokenStorage.setLocalDataOwner(effect.userId)
                SessionEffect.ClearProfilePending -> tokenStorage.setProfileSetupPending(false)
                is SessionEffect.OpenAccountScope -> accountScope.open(effect.userId)
                SessionEffect.CloseAccountScope -> accountScope.close()
            }
        } catch (e: Exception) {
            // One failed effect must not strand the others: a wipe that throws on a locked database
            // still has to be followed by closing the scope. Logged, never silent (#264).
            Log.e(TAG, "Session effect $effect failed: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "SessionManager"
    }
}

/** [SessionManager.signIn] with what a verification endpoint returns. */
fun SessionManager.signIn(response: AuthTokenResponse) = signIn(
    accessToken = response.accessToken,
    refreshToken = response.refreshToken,
    userId = response.userId,
    deviceId = response.deviceId,
    isNewUser = response.isNewUser
)

/**
 * How the HTTP client reports what happened to a token refresh. The client does not decide what a
 * rejection means for the session — it used to, by clearing the token store, and nothing else in the
 * app found out (#813).
 */
interface RefreshOutcomeListener {
    fun onRefreshStarted()
    fun onRefreshSucceeded()
    fun onRefreshFailedTransiently()
    fun onRefreshRejected()

    companion object {
        /** For the clients built in tests that do not care about the session. */
        val None: RefreshOutcomeListener = object : RefreshOutcomeListener {
            override fun onRefreshStarted() = Unit
            override fun onRefreshSucceeded() = Unit
            override fun onRefreshFailedTransiently() = Unit
            override fun onRefreshRejected() = Unit
        }
    }
}

/**
 * The account's on-device data, as the session needs it: something to delete.
 *
 * An interface because the real store is the SQLDelight-backed `LocalCache`, which needs a platform
 * driver no `commonTest` can construct.
 */
fun interface AccountDataStore {
    fun wipe()
}

/**
 * The per-account dependency scope (#829): opened when an account's session starts, closed when it
 * ends, so a holder created for one account can never be handed to the next.
 */
interface AccountScope {
    fun open(userId: String)
    fun close()
}

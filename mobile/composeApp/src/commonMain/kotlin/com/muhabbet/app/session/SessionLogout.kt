package com.muhabbet.app.session

import com.muhabbet.app.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A user-initiated logout, run where leaving the screen cannot stop it.
 *
 * Logging out revokes the session on the server first (#752), which can take up to the
 * repository's timeout. Run from the Settings screen's own coroutine scope, a user who left the
 * screen in that time cancelled it: the tokens stayed, the app never went back to the login screen,
 * and the button they had pressed appeared to do nothing. [scope] belongs to the app, not a screen.
 *
 * @param scope app-lifetime, on the main dispatcher in production because [onLoggedOut] navigates.
 */
class SessionLogout(
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope
) {
    /** Revokes, clears the local session, then calls [onLoggedOut] — whatever happens to the caller. */
    fun logout(onLoggedOut: () -> Unit): Job = scope.launch {
        authRepository.logout()
        onLoggedOut()
    }
}

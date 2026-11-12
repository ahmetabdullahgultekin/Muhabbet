package com.muhabbet.app.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "The server has ended this session", from the place that learns it to the place that can act.
 *
 * [com.muhabbet.app.data.remote.ApiClient] learns it — a refused refresh token — deep inside a Ktor
 * plugin, with no way to reach navigation. `App.kt` can reach navigation and nothing else. Before
 * this existed the client cleared the tokens and stopped there, so the app stayed on the signed-in
 * screens with no session behind them and the teardown that runs on the way back to the login
 * screen never ran (#752).
 *
 * A [StateFlow] rather than an event stream on purpose: a refusal can happen during start-up,
 * before anything collects, and an event emitted then would simply be lost. The flag stays raised
 * until [acknowledge] says the app has acted on it.
 *
 * A Koin singleton for the same reason as every other controller here: one writer, one reader, and
 * a second copy would be a signal nobody hears.
 */
class SessionRevocation {
    private val _revoked = MutableStateFlow(false)
    val revoked: StateFlow<Boolean> = _revoked

    fun signal() {
        _revoked.value = true
    }

    fun acknowledge() {
        _revoked.value = false
    }
}

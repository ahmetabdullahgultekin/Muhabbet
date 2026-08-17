package com.muhabbet.app.data.local

import com.muhabbet.app.config.AppLockTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the App Lock (#378) enabled flag + grace-period choice as observable state, write-through
 * to [TokenStorage].
 *
 * A singleton for the same reason as [ThemeController] and `PrivacySettingsController`: the value
 * is read in two places that must not disagree — `AppLockScreen` writes it, `AppLockGate` (mounted
 * once, above the whole authenticated app) reads it to decide whether to demand re-authentication.
 * A plain `tokenStorage.getAppLockEnabled()` read inside `AppLockGate` would work at the moment it
 * first composes and never again, because nothing about flipping a `SharedPreferences` value
 * through a *different* composable recomposes this one. That is the exact bug class #377 already
 * fixed once for the read-receipts switch — two independent readers of the same mutable field.
 */
class AppLockController(private val tokenStorage: TokenStorage) {

    private val _enabled = MutableStateFlow(tokenStorage.getAppLockEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _timeout = MutableStateFlow(tokenStorage.getAppLockTimeout() ?: AppLockTimeout.DEFAULT)
    val timeout: StateFlow<String> = _timeout.asStateFlow()

    fun setEnabled(value: Boolean) {
        tokenStorage.setAppLockEnabled(value)
        _enabled.value = value
        if (!value) {
            // Disabling clears the timeout choice too, so re-enabling later starts from
            // AppLockTimeout.DEFAULT rather than resurrecting a stale, possibly-stricter choice
            // the user never re-confirmed.
            tokenStorage.setAppLockTimeout(AppLockTimeout.DEFAULT)
            _timeout.value = AppLockTimeout.DEFAULT
        }
    }

    fun setTimeout(option: String) {
        tokenStorage.setAppLockTimeout(option)
        _timeout.value = option
    }
}

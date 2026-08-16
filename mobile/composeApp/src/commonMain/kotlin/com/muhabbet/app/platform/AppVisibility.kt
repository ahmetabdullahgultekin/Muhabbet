package com.muhabbet.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app is in front of the user right now.
 *
 * Compose gives no answer to this. A `LaunchedEffect` keyed on anything stable does not re-run when
 * the phone is locked and unlocked — the composition is never torn down, so an open chat's
 * open-handler fires exactly once, when the chat was opened, and never again for the rest of the
 * session (#478). Anything that has to happen "when the user looks at this again" needs a signal
 * from outside the composition, and this is it.
 *
 * The signal is fed in `App.kt` from the Decompose/Essenty lifecycle that `RootComponent` already
 * carries — on Android `defaultComponentContext()` binds that to the hosting Activity, so `onResume`
 * and `onPause` are genuine foreground transitions. Nothing new observes the platform; this only
 * republishes what the navigation library is already told.
 *
 * Defaults to **foreground**. iOS has no entry point yet (no `MainViewController`), so nothing feeds
 * this there. A default of `false` would make an unfed platform behave as if the app were permanently
 * hidden and suppress work that runs today; defaulting to `true` means an unfed platform behaves
 * exactly as it did before this type existed.
 */
class AppVisibility {

    private val _isForeground = MutableStateFlow(true)

    /**
     * `true` while the app is resumed. A `StateFlow`, so a collector that subscribes later still
     * learns the current state, and repeated reports of the same state emit nothing.
     */
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    fun onForeground() {
        _isForeground.value = true
    }

    fun onBackground() {
        _isForeground.value = false
    }
}

package com.muhabbet.app.data.local

import com.muhabbet.designsystem.theme.MuhabbetThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the selected theme as observable state, write-through to [TokenStorage].
 *
 * Exists so that choosing a theme repaints instead of restarting the process. `ThemeSection` used to
 * persist the choice and then call `restartApp()`; the restart was not what applied the theme (the
 * theme never read the stored value at all), it merely hid that nothing had happened. Language still
 * restarts, because on Android the locale is applied in `MainActivity.onCreate`.
 *
 * A singleton rather than composable state: the value is read at the composition root, above the
 * theme, and must survive every recomposition below it.
 */
class ThemeController(private val tokenStorage: TokenStorage) {

    private val _mode = MutableStateFlow(MuhabbetThemeMode.fromStorageKey(tokenStorage.getTheme()))
    val mode: StateFlow<MuhabbetThemeMode> = _mode.asStateFlow()

    /**
     * Whether haptic feedback fires at all.
     *
     * Lives here rather than in a controller of its own: it is the same shape — read once at the
     * composition root, written through to storage — and it is handed to the theme alongside the
     * mode, so the two travel together.
     */
    private val _hapticsEnabled = MutableStateFlow(tokenStorage.getHapticsEnabled())
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    fun set(mode: MuhabbetThemeMode) {
        tokenStorage.setTheme(mode.storageKey)
        _mode.value = mode
    }

    fun setHapticsEnabled(enabled: Boolean) {
        tokenStorage.setHapticsEnabled(enabled)
        _hapticsEnabled.value = enabled
    }
}

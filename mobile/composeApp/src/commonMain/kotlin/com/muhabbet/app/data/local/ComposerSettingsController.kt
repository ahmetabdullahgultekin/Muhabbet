package com.muhabbet.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Composer preferences as observable state, write-through to [TokenStorage].
 *
 * One field today: whether Enter sends (#516). It is a singleton rather than screen state for the
 * reason every other controller in this package is one — the switch lives in Settings and the
 * behaviour it controls lives in the chat composer, so two screens read the same value. Two
 * `remember { mutableStateOf }` copies is how the app once showed two read-receipt switches that
 * disagreed; the second copy here would be worse than merely wrong, because the disagreeing half is
 * invisible: the composer would keep the behaviour it had when it was first composed and the switch
 * would show the new one.
 *
 * Named for the composer rather than for the setting so the next composer preference has an obvious
 * home — the same reason [ThemeController] also carries haptics.
 */
class ComposerSettingsController(private val tokenStorage: TokenStorage) {

    private val _enterToSend = MutableStateFlow(tokenStorage.getEnterToSend())

    /** True when Enter sends and Shift+Enter inserts a newline; false for the reverse. */
    val enterToSend: StateFlow<Boolean> = _enterToSend.asStateFlow()

    fun setEnterToSend(enabled: Boolean) {
        tokenStorage.setEnterToSend(enabled)
        _enterToSend.value = enabled
    }
}

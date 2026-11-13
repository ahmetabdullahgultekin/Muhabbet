package com.muhabbet.app.ui.settings

import com.muhabbet.app.data.local.AppLockController
import com.muhabbet.app.presentation.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

data class AppLockSettingsUiState(
    val enabled: Boolean,
    /** One of the [com.muhabbet.app.config.AppLockTimeout] keys. */
    val timeout: String,
    /** The last attempt to turn the lock on failed to authenticate. */
    val authFailed: Boolean = false
)

sealed interface AppLockSettingsEvent {
    /** The switch was flipped; [on] is the position the user asked for, not one already granted. */
    data class ToggleRequested(val on: Boolean) : AppLockSettingsEvent
    data class AuthenticationFinished(val success: Boolean) : AppLockSettingsEvent
    data class TimeoutSelected(val timeout: String) : AppLockSettingsEvent
}

sealed interface AppLockSettingsEffect {
    /** Ask the platform to prove the credential works before the lock is armed. */
    data object Authenticate : AppLockSettingsEffect
}

/**
 * App Lock settings (#378) as a Store (ADR 0010) — the writer half; `AppLockGate` is the reader.
 *
 * The rule this encodes is the one the screen used to hold in a composable: turning the lock ON is
 * not persisted until a platform authentication succeeds, because a switch that remembered its
 * position before the credential was proven to work would look armed and not be. Turning it OFF
 * needs no proof — leaving a lock is never the security-sensitive direction. As a Store the rule is
 * now a unit test rather than a comment.
 *
 * [AppLockController] stays the single source of truth for the persisted values; this Store
 * observes it and adds only the one piece of screen state the controller does not own.
 */
class AppLockSettingsStore(
    private val controller: AppLockController,
    context: CoroutineContext = Dispatchers.Main.immediate
) : Store<AppLockSettingsUiState, AppLockSettingsEvent, AppLockSettingsEffect>(
    initialState = AppLockSettingsUiState(enabled = controller.enabled.value, timeout = controller.timeout.value),
    context = context
) {

    init {
        scope.launch {
            combine(controller.enabled, controller.timeout) { enabled, timeout -> enabled to timeout }
                .collect { (enabled, timeout) -> updateState { it.copy(enabled = enabled, timeout = timeout) } }
        }
    }

    override fun onEvent(event: AppLockSettingsEvent) {
        when (event) {
            is AppLockSettingsEvent.ToggleRequested -> if (event.on) {
                updateState { it.copy(authFailed = false) }
                emit(AppLockSettingsEffect.Authenticate)
            } else {
                controller.setEnabled(false)
            }
            is AppLockSettingsEvent.AuthenticationFinished -> {
                updateState { it.copy(authFailed = !event.success) }
                if (event.success) controller.setEnabled(true)
            }
            is AppLockSettingsEvent.TimeoutSelected -> controller.setTimeout(event.timeout)
        }
    }
}

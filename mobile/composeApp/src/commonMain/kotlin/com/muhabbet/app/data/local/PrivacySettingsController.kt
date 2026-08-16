package com.muhabbet.app.data.local

import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.dto.PrivacySettingsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The account's privacy settings as observable state, backed by `/api/v1/users/me/privacy`.
 *
 * A singleton for one reason: the read-receipts switch appears on **two** screens (Settings →
 * Gizlilik and the KVKK privacy dashboard). Each used to hold its own `remember { mutableStateOf }`,
 * so the two could show opposite answers at the same time and neither matched the server. Sharing
 * one flow makes disagreement unrepresentable.
 *
 * State is deliberately nullable until the first successful load. There is no honest default to
 * show: the old screens assumed "everyone" for every control, which displayed the most permissive
 * setting to a user who may have chosen the strictest — and then wrote that guess back on the next
 * edit. Callers render a loading or error state instead of inventing a value.
 */
class PrivacySettingsController(private val authRepository: AuthRepository) {

    private val _settings = MutableStateFlow<PrivacySettingsResponse?>(null)
    val settings: StateFlow<PrivacySettingsResponse?> = _settings.asStateFlow()

    /**
     * Fetches the stored settings. Safe to call on every screen entry; the result replaces whatever
     * is cached, so a change made on another device shows up on the next open.
     */
    suspend fun load(): Result<PrivacySettingsResponse> =
        runCatchingCancellable { authRepository.getPrivacySettings() }
            .onSuccess { _settings.value = it }
            .onFailure { Log.e(TAG, "Failed to load privacy settings", it) }

    /**
     * Applies one change optimistically, then reconciles with the server's answer.
     *
     * On failure the previous value is restored so the control snaps back rather than displaying a
     * setting the server never accepted — `ApiClient` throws on any non-2xx, so a rejected PATCH
     * lands here and not in the success path.
     */
    suspend fun update(
        readReceiptsEnabled: Boolean? = null,
        onlineStatusVisibility: String? = null,
        aboutVisibility: String? = null
    ): Result<PrivacySettingsResponse> {
        val previous = _settings.value
            ?: return Result.failure(IllegalStateException("PRIVACY_NOT_LOADED"))

        _settings.value = previous.copy(
            readReceiptsEnabled = readReceiptsEnabled ?: previous.readReceiptsEnabled,
            onlineStatusVisibility = onlineStatusVisibility ?: previous.onlineStatusVisibility,
            aboutVisibility = aboutVisibility ?: previous.aboutVisibility
        )

        return runCatchingCancellable {
            authRepository.updatePrivacySettings(
                readReceiptsEnabled = readReceiptsEnabled,
                onlineStatusVisibility = onlineStatusVisibility,
                aboutVisibility = aboutVisibility
            )
        }
            .onSuccess { _settings.value = it }
            .onFailure {
                _settings.value = previous
                Log.e(TAG, "Failed to update privacy settings", it)
            }
    }

    private companion object {
        const val TAG = "PrivacySettingsController"
    }
}

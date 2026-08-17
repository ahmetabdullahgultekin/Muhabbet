package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.PushTokenProvider
import com.muhabbet.app.util.Log

/**
 * Registers this device's push token with the backend so a push notification has an address to
 * go to (#398).
 *
 * Pulled out of the Compose layer because it has two call sites with nothing Composable about
 * either: the login / app-start effect in `App.kt`, and `MuhabbetFirebaseMessagingService
 * .onNewToken` firing on a system callback when Firebase rotates the token. The second call site
 * used to be a comment — "Token will be registered when the app next connects via App.kt" — with
 * no code behind it, so a rotated token was never re-sent and the row on the server went stale
 * without anything ever removing it.
 */
class PushTokenRegistrar(
    private val pushTokenProvider: PushTokenProvider,
    private val authRepository: AuthRepository,
    private val tokenStorage: TokenStorage
) {

    /**
     * No-op when there is no session to attach a token to. Never throws: both call sites — a
     * Compose effect with no screen of its own, and a system callback — have nothing to show a
     * user, so the log line is the only record of a failure (#264).
     *
     * @param knownToken the token if the caller already has it (`onNewToken` receives one
     * directly), which skips a redundant round trip through [PushTokenProvider]. Left null to
     * fetch the current token instead.
     * @param languageTag the language the app is **rendering**, which App.kt reads out of
     * `strings.xml` itself. Notification text is composed on the server, so without this the server
     * has no way to know it and falls back to Turkish for everyone (#469). Left null by
     * `onNewToken`, which runs in a service with no composition and possibly before the app has
     * ever started in this process; the saved preference then answers, and if there is none the
     * server keeps whatever this device last registered.
     */
    suspend fun registerIfLoggedIn(knownToken: String? = null, languageTag: String? = null) {
        if (!tokenStorage.isLoggedIn()) return
        try {
            val token = knownToken ?: pushTokenProvider.getToken()
            if (token == null) {
                Log.w(TAG, "Push token unavailable: FCM returned null")
                return
            }
            val language = languageTag?.takeIf { it.isNotBlank() }
                ?: tokenStorage.getLanguage()?.takeIf { it.isNotBlank() }
            authRepository.registerPushToken(token, language)
            Log.d(TAG, "Push token registered: ${token.take(10)}..., language=$language")
        } catch (e: Exception) {
            Log.e(TAG, "Push token registration failed: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "PushTokenRegistrar"
    }
}

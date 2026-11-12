package com.muhabbet.app.session

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.repository.AuthRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A logout the user confirmed finishes — tokens cleared and the app back on the login screen — even
 * when the screen that started it is gone before the server answers.
 */
class SessionLogoutTest {

    @Test
    fun should_clear_tokens_and_leave_for_login_when_the_screen_is_gone_before_the_server_answers() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val tokenStorage = FakeTokenStorage().apply { saveTokens("access", "refresh", "user1", "device1") }
        val apiClient = ApiClient(tokenStorage, MockEngine {
            requestStarted.complete(Unit)
            release.await()
            respond("", HttpStatusCode.NoContent)
        })
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val logout = SessionLogout(AuthRepository(apiClient, tokenStorage), appScope)
        val navigatedToLogin = CompletableDeferred<Unit>()

        withContext(Dispatchers.Default) {
            val screenScope = CoroutineScope(SupervisorJob())
            screenScope.launch { logout.logout { navigatedToLogin.complete(Unit) } }
            requestStarted.await()
            screenScope.cancel() // the user left Settings
            release.complete(Unit)
            navigatedToLogin.await()
        }
        appScope.cancel()

        assertTrue(navigatedToLogin.isCompleted)
        assertFalse(tokenStorage.isLoggedIn(), "leaving the screen kept the user signed in")
    }
}

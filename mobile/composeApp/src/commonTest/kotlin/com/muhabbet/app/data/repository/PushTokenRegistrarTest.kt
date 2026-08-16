package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.platform.PushTokenProvider
import com.muhabbet.shared.dto.RegisterPushTokenRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Zero of six production devices ever stored a push token (#398). The registration call itself —
 * [AuthRepository.registerPushToken] — was never broken; what broke was that nothing reliably
 * invoked it. This exercises [PushTokenRegistrar] in isolation from the Compose effect that used
 * to own this logic, so the login/no-login/rotation/failure cases are testable without an
 * emulator, which this host does not have.
 */
class PushTokenRegistrarTest {

    private companion object {
        const val PUSH_TOKEN_PATH = "/api/v1/devices/push-token"
    }

    /** Records every request and answers with a configurable status. */
    private class Backend(private val status: HttpStatusCode = HttpStatusCode.NoContent) {
        val requestedPaths = mutableListOf<String>()
        val requestedTokens = mutableListOf<String>()

        private val json = Json { ignoreUnknownKeys = true }

        private val engine = MockEngine { request ->
            requestedPaths += request.url.encodedPath
            if (request.url.encodedPath == PUSH_TOKEN_PATH && request.method == HttpMethod.Put) {
                val body = json.decodeFromString<RegisterPushTokenRequest>(
                    request.body.toByteArray().decodeToString()
                )
                requestedTokens += body.pushToken
            }
            respond(
                content = if (status == HttpStatusCode.NoContent) "" else {
                    """{"error":{"code":"INTERNAL_ERROR","message":"nope"},"timestamp":"2026-08-16T10:00:00Z"}"""
                },
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val tokenStorage = FakeTokenStorage()
        val apiClient = ApiClient(tokenStorage, engine)
        val authRepository = AuthRepository(apiClient, tokenStorage)

        fun loggedIn() {
            tokenStorage.saveTokens("access", "refresh", "user-1", "device-1")
        }
    }

    private class FakePushTokenProvider(private var token: String? = "fresh-fcm-token") : PushTokenProvider {
        var callCount = 0
            private set

        override suspend fun getToken(): String? {
            callCount++
            return token
        }
    }

    @Test
    fun registerIfLoggedIn_whenNotLoggedIn_neverCallsTheProviderOrTheServer() = runTest {
        val backend = Backend()
        val provider = FakePushTokenProvider()
        val registrar = PushTokenRegistrar(provider, backend.authRepository, backend.tokenStorage)

        registrar.registerIfLoggedIn()

        assertEquals(0, provider.callCount)
        assertTrue(backend.requestedPaths.isEmpty(), "a logged-out device must send no push token")
    }

    @Test
    fun registerIfLoggedIn_whenLoggedIn_fetchesAndRegistersTheCurrentToken() = runTest {
        val backend = Backend().apply { loggedIn() }
        val provider = FakePushTokenProvider(token = "fresh-fcm-token")
        val registrar = PushTokenRegistrar(provider, backend.authRepository, backend.tokenStorage)

        registrar.registerIfLoggedIn()

        assertEquals(1, provider.callCount)
        assertEquals(listOf(PUSH_TOKEN_PATH), backend.requestedPaths)
        assertEquals(listOf("fresh-fcm-token"), backend.requestedTokens)
    }

    @Test
    fun registerIfLoggedIn_whenGivenAKnownToken_registersItWithoutAskingTheProviderAgain() = runTest {
        // onNewToken already has the rotated value; a second call to FirebaseMessaging.getInstance()
        // .token would just await the same value back at the cost of a round trip.
        val backend = Backend().apply { loggedIn() }
        val provider = FakePushTokenProvider(token = "stale-token-the-provider-would-return")
        val registrar = PushTokenRegistrar(provider, backend.authRepository, backend.tokenStorage)

        registrar.registerIfLoggedIn(knownToken = "rotated-token-from-onNewToken")

        assertEquals(0, provider.callCount)
        assertEquals(listOf("rotated-token-from-onNewToken"), backend.requestedTokens)
    }

    @Test
    fun registerIfLoggedIn_whenFcmReturnsNoToken_sendsNoRequest() = runTest {
        val backend = Backend().apply { loggedIn() }
        val provider = FakePushTokenProvider(token = null)
        val registrar = PushTokenRegistrar(provider, backend.authRepository, backend.tokenStorage)

        registrar.registerIfLoggedIn()

        assertTrue(backend.requestedPaths.isEmpty())
    }

    @Test
    fun registerIfLoggedIn_whenTheServerRejectsTheToken_doesNotThrow() = runTest {
        // Neither call site can show the user anything (#264) — a Compose effect with no screen of
        // its own, and a system callback. The failure must be absorbed, not propagated.
        val backend = Backend(status = HttpStatusCode.InternalServerError).apply { loggedIn() }
        val provider = FakePushTokenProvider()
        val registrar = PushTokenRegistrar(provider, backend.authRepository, backend.tokenStorage)

        registrar.registerIfLoggedIn()

        assertEquals(listOf(PUSH_TOKEN_PATH), backend.requestedPaths)
    }
}

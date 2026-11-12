package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private fun createApiClientWithMock(handler: suspend (io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData)): Pair<ApiClient, FakeTokenStorage> {
        val tokenStorage = FakeTokenStorage()
        val mockEngine = MockEngine(handler)
        // The engine goes into ApiClient itself. Building a second HttpClient around the mock and
        // handing back a default-engine ApiClient (what this helper used to do) left every caller
        // talking to the real server.
        val apiClient = ApiClient(tokenStorage, mockEngine)
        return apiClient to tokenStorage
    }

    @Test
    fun should_return_false_when_not_logged_in() {
        val tokenStorage = FakeTokenStorage()
        val apiClient = ApiClient(tokenStorage)
        val repo = AuthRepository(apiClient, tokenStorage)

        assertFalse(repo.isLoggedIn())
    }

    @Test
    fun should_return_true_when_logged_in() {
        val tokenStorage = FakeTokenStorage()
        tokenStorage.saveTokens("access", "refresh", "user1", "device1")
        val apiClient = ApiClient(tokenStorage)
        val repo = AuthRepository(apiClient, tokenStorage)

        assertTrue(repo.isLoggedIn())
    }

    @Test
    fun should_revoke_the_session_on_the_server_before_clearing_tokens_when_logging_out() = runTest {
        // #752: the app never called /auth/logout, so the refresh token on a logged-out phone stayed
        // valid on the server for its whole lifetime. The call needs the token, so it must go first.
        val logoutCalls = mutableListOf<String?>()
        val (apiClient, tokenStorage) = createApiClientWithMock { request ->
            if (request.url.encodedPath == "/api/v1/auth/logout") {
                logoutCalls += request.headers[HttpHeaders.Authorization]
                respond("", HttpStatusCode.NoContent)
            } else {
                respond("Not found", HttpStatusCode.NotFound)
            }
        }
        tokenStorage.saveTokens("access", "refresh", "user1", "device1")
        val repo = AuthRepository(apiClient, tokenStorage)

        // On a real dispatcher: logout() bounds the call with withTimeoutOrNull, and on runTest's
        // virtual clock that timeout fires the instant the scheduler goes idle waiting for the
        // mock engine, which runs on its own threads.
        withContext(Dispatchers.Default) { repo.logout() }

        assertEquals(listOf<String?>("Bearer access"), logoutCalls)
        assertFalse(repo.isLoggedIn())
    }

    @Test
    fun should_still_clear_tokens_when_the_server_logout_fails() = runTest {
        // Best-effort: a user who taps "log out" offline is logged out locally regardless.
        val (apiClient, tokenStorage) = createApiClientWithMock {
            respond("", HttpStatusCode.InternalServerError)
        }
        tokenStorage.saveTokens("access", "refresh", "user1", "device1")
        val repo = AuthRepository(apiClient, tokenStorage)

        // On a real dispatcher: logout() bounds the call with withTimeoutOrNull, and on runTest's
        // virtual clock that timeout fires the instant the scheduler goes idle waiting for the
        // mock engine, which runs on its own threads.
        withContext(Dispatchers.Default) { repo.logout() }

        assertFalse(repo.isLoggedIn())
    }

    @Test
    fun should_clear_tokens_when_the_caller_is_cancelled_during_the_server_logout() = runTest {
        // The user taps "log out", nothing visibly happens on a slow network, and they leave
        // Settings — cancelling the screen's scope mid-call. They must still end up logged out.
        val requestStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val (apiClient, tokenStorage) = createApiClientWithMock {
            requestStarted.complete(Unit)
            release.await()
            respond("", HttpStatusCode.NoContent)
        }
        tokenStorage.saveTokens("access", "refresh", "user1", "device1")
        val repo = AuthRepository(apiClient, tokenStorage)

        withContext(Dispatchers.Default) {
            val screen = launch { repo.logout() }
            requestStarted.await()
            screen.cancel()
            release.complete(Unit)
            screen.join()
        }

        assertFalse(repo.isLoggedIn(), "leaving the screen kept the user signed in")
    }

    @Test
    fun should_save_tokens_on_successful_verify_otp() = runTest {
        val (apiClient, tokenStorage) = createApiClientWithMock { request ->
            if (request.url.encodedPath == "/api/v1/auth/otp/verify") {
                respond(
                    content = """
                        {
                            "data": {
                                "accessToken": "new-access-token",
                                "refreshToken": "new-refresh-token",
                                "expiresIn": 3600,
                                "userId": "user-123",
                                "deviceId": "device-456",
                                "isNewUser": false
                            },
                            "timestamp": "2026-02-13T12:00:00Z"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("Not found", HttpStatusCode.NotFound)
            }
        }
        val repo = AuthRepository(apiClient, tokenStorage)
        assertFalse(tokenStorage.isLoggedIn())

        // Previously this test wrote the tokens itself and asserted its own writes back out, so it
        // exercised FakeTokenStorage rather than the repository. Drive verifyOtp for real.
        repo.verifyOtp("+905000000001", "123456", "Pixel", "ANDROID")

        assertTrue(tokenStorage.isLoggedIn())
        assertEquals("new-access-token", tokenStorage.getAccessToken())
        assertEquals("new-refresh-token", tokenStorage.getRefreshToken())
        assertEquals("user-123", tokenStorage.getUserId())
        assertEquals("device-456", tokenStorage.getDeviceId())
    }

    @Test
    fun should_not_save_tokens_when_verify_otp_is_rejected() = runTest {
        val (apiClient, tokenStorage) = createApiClientWithMock {
            respond(
                content = """{"error":{"code":"AUTH_OTP_INVALID","message":"gecersiz"},"timestamp":"2026-01-01T00:00:00Z"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val repo = AuthRepository(apiClient, tokenStorage)

        assertFailsWith<Exception> { repo.verifyOtp("+905000000001", "000000", "Pixel", "ANDROID") }

        assertFalse(tokenStorage.isLoggedIn())
    }

    @Test
    fun should_throw_on_failed_otp_request() = runTest {
        // Previously this built a default-engine ApiClient and relied on the request failing to
        // connect, so it passed for as long as BASE_URL pointed at an unroutable host — and sent a
        // real OTP request to production the moment the host was correct. Drive the failure from a
        // mocked server response instead.
        val (apiClient, tokenStorage) = createApiClientWithMock {
            respond(
                content = """{"error":{"code":"AUTH_OTP_COOLDOWN","message":"cooldown"},"timestamp":"2026-01-01T00:00:00Z"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val repo = AuthRepository(apiClient, tokenStorage)

        // assertFailsWith<Exception> alone would also pass on a SerializationException from a
        // changed envelope, which is a crash the user sees as untranslatable text rather than the
        // cooldown notice. Assert on what was actually propagated.
        val error = assertFailsWith<Exception> {
            repo.requestOtp("+905000000001")
        }
        assertEquals("cooldown", error.message)
    }
}

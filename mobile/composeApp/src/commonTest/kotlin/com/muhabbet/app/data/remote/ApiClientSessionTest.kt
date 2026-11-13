package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.session.SessionEndReason
import com.muhabbet.app.session.SessionManager
import com.muhabbet.app.session.SessionState
import com.muhabbet.app.session.testSessionManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The HTTP client's half of the session (#813): what it reports when a refresh is rejected, and that
 * it never sends a token the store no longer holds.
 */
class ApiClientSessionTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /** Every Authorization header the server saw on `/api/v1/users/me`, in order. */
    private val seenAuthorization = mutableListOf<String?>()

    private fun client(
        storage: FakeTokenStorage,
        session: SessionManager,
        meStatus: () -> HttpStatusCode = { HttpStatusCode.OK },
        refresh: () -> Pair<HttpStatusCode, String>
    ) = ApiClient(
        storage,
        MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/token/refresh" -> {
                    val (status, body) = refresh()
                    respond(body, status, jsonHeaders)
                }
                else -> {
                    seenAuthorization += request.headers[HttpHeaders.Authorization]
                    val status = meStatus()
                    if (status == HttpStatusCode.OK) {
                        respond("""{"data":null,"timestamp":""}""", status, jsonHeaders)
                    } else {
                        respond("""{"error":{"code":"AUTH_UNAUTHORIZED","message":"x"}}""", status, jsonHeaders)
                    }
                }
            }
        },
        refreshOutcomes = session
    )

    @Test
    fun should_expire_the_session_when_the_server_rejects_the_refresh_token() = runTest {
        val storage = FakeTokenStorage().apply { saveTokens("old-access", "old-refresh", "a", "d") }
        val session = testSessionManager(storage)
        val api = client(storage, session, meStatus = { HttpStatusCode.Unauthorized }) {
            HttpStatusCode.Unauthorized to """{"error":{"code":"AUTH_TOKEN_INVALID","message":"x"}}"""
        }

        assertFailsWith<ApiException> { api.get<Unit>("/api/v1/users/me") }

        assertEquals(SessionState.Expired(SessionEndReason.REFRESH_REJECTED), session.state.value)
        assertNull(storage.getAccessToken(), "the session clears the credentials, once, in one place")
    }

    @Test
    fun should_keep_the_session_when_the_refresh_cannot_reach_a_working_server() = runTest {
        val storage = FakeTokenStorage().apply { saveTokens("old-access", "old-refresh", "a", "d") }
        val session = testSessionManager(storage)
        val api = client(storage, session, meStatus = { HttpStatusCode.Unauthorized }) {
            HttpStatusCode.ServiceUnavailable to "{}"
        }

        assertFailsWith<ApiException> { api.get<Unit>("/api/v1/users/me") }

        assertEquals(SessionState.SignedIn("a"), session.state.value)
        assertEquals("old-access", storage.getAccessToken())
    }

    @Test
    fun should_retry_with_the_renewed_token_and_return_to_signed_in_after_a_refresh() = runTest {
        val storage = FakeTokenStorage().apply { saveTokens("old-access", "old-refresh", "a", "d") }
        val session = testSessionManager(storage)
        var calls = 0
        val api = client(
            storage,
            session,
            meStatus = { if (calls++ == 0) HttpStatusCode.Unauthorized else HttpStatusCode.OK }
        ) {
            HttpStatusCode.OK to """{"data":{"accessToken":"new-access","refreshToken":"new-refresh"},"timestamp":""}"""
        }

        api.get<Unit>("/api/v1/users/me")

        assertEquals(listOf<String?>("Bearer old-access", "Bearer new-access"), seenAuthorization)
        assertEquals(SessionState.SignedIn("a"), session.state.value)
        assertEquals("new-refresh", storage.getRefreshToken())
    }

    @Test
    fun should_send_the_new_accounts_token_on_the_first_request_after_switching_accounts() = runTest {
        val storage = FakeTokenStorage().apply { saveTokens("token-of-a", "refresh-of-a", "a", "d") }
        val session = testSessionManager(storage)
        val api = client(storage, session) { HttpStatusCode.Unauthorized to "{}" }

        api.get<Unit>("/api/v1/users/me")
        session.logout()
        session.signIn("token-of-b", "refresh-of-b", "b", "d2", isNewUser = false)
        api.get<Unit>("/api/v1/users/me")

        // With Ktor's bearer cache in place the second request still carried A's token: the client is
        // a process-wide singleton and nothing ever invalidated what loadTokens returned first.
        assertEquals(listOf<String?>("Bearer token-of-a", "Bearer token-of-b"), seenAuthorization)
    }
}

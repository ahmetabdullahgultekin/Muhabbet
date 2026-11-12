package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The server refusing the refresh token is a session ending, and the app has to be told (#752).
 *
 * The client used to clear the tokens and stop. Nothing navigated, so the session teardown that
 * runs on the way back to the login screen never ran and the account's messages stayed on disk.
 */
class ApiClientRefreshRejectedTest {

    private val jsonContentType =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun client(storage: FakeTokenStorage, refresh: () -> HttpStatusCode, onRejected: () -> Unit) =
        ApiClient(
            storage,
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/api/v1/auth/token/refresh" -> {
                        val status = refresh()
                        respond("""{"error":{"code":"AUTH_TOKEN_INVALID","message":"x"}}""", status, jsonContentType)
                    }
                    else -> respond(
                        """{"error":{"code":"AUTH_UNAUTHORIZED","message":"x"}}""",
                        HttpStatusCode.Unauthorized,
                        jsonContentType
                    )
                }
            },
            onRefreshRejected = onRejected
        )

    @Test
    fun `should report the session as ended when the server rejects the refresh token`() = runTest {
        val storage = FakeTokenStorage().apply { saveTokens("expired", "revoked", "user-1", "device-1") }
        var rejections = 0
        val api = client(storage, refresh = { HttpStatusCode.Unauthorized }, onRejected = { rejections++ })

        runCatching { api.get<Unit>("/api/v1/users/me") }

        assertEquals(1, rejections)
        assertFalse(storage.isLoggedIn())
    }

    @Test
    fun `should not end the session when the refresh fails for a reason other than rejection`() = runTest {
        // A server error is not a verdict on the token; logging the user out over a 502 would erase
        // their messages for an outage.
        val storage = FakeTokenStorage().apply { saveTokens("expired", "fine", "user-1", "device-1") }
        var rejections = 0
        val api = client(storage, refresh = { HttpStatusCode.BadGateway }, onRejected = { rejections++ })

        runCatching { api.get<Unit>("/api/v1/users/me") }

        assertEquals(0, rejections)
        assertTrue(storage.isLoggedIn())
    }
}

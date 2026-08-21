package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.repository.AuthRepository
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

/**
 * How many requests one rejected verification code costs (#400).
 *
 * `AUTH_OTP_INVALID` is **401**, and Ktor's `Auth` plugin retries every 401 it sees, because a 401
 * is normally a challenge for a token. On the four pre-login paths it is not: those are the
 * endpoints [ApiClient.PRE_LOGIN_PATHS] deliberately withholds the bearer token from, so the plugin
 * has no token version recorded for the request, skips the refresh entirely — no
 * `/auth/token/refresh` is ever sent, which is what makes this so hard to see in a log — and
 * replays the request unchanged.
 *
 * The server claims an attempt *before* it compares the code (`AuthService.verifyOtp`), so both
 * copies counted. The configured five attempts were really two and a half, and the third mistyped
 * code locked the user out of a login they were entitled to retry.
 *
 * Measured on this branch before the fix: **2** verify requests, **0** refresh requests, with and
 * without stored tokens.
 */
class PreLoginRetryTest {

    private val jsonContentType =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private class Traffic {
        var verify = 0
        var firebaseVerify = 0
        var refresh = 0
    }

    /** An engine that rejects every pre-login call the way production does, and counts the calls. */
    private fun clientRejectingEverything(traffic: Traffic, tokenStorage: FakeTokenStorage) =
        ApiClient(tokenStorage, MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/token/refresh" -> {
                    traffic.refresh++
                    respond(
                        """{"data":{"accessToken":"fresh-access","refreshToken":"fresh-refresh"},"timestamp":"2026-08-21T09:50:16Z"}""",
                        HttpStatusCode.OK,
                        jsonContentType,
                    )
                }

                "/api/v1/auth/firebase-verify" -> {
                    traffic.firebaseVerify++
                    respond(
                        """{"error":{"code":"AUTH_TOKEN_INVALID","message":"Firebase token geçersiz"},"timestamp":"2026-08-21T09:50:16Z"}""",
                        HttpStatusCode.Unauthorized,
                        jsonContentType,
                    )
                }

                else -> {
                    traffic.verify++
                    respond(
                        """{"error":{"code":"AUTH_OTP_INVALID","message":"Geçersiz doğrulama kodu"},"timestamp":"2026-08-21T09:50:16Z"}""",
                        HttpStatusCode.Unauthorized,
                        jsonContentType,
                    )
                }
            }
        })

    @Test
    fun rejectedCode_costsExactlyOneVerifyRequest() = runTest {
        val traffic = Traffic()
        val storage = FakeTokenStorage()
        val repo = AuthRepository(clientRejectingEverything(traffic, storage), storage)

        assertFailsWith<ApiException> {
            repo.verifyOtp("+905000000001", "000000", "Pixel", "ANDROID")
        }

        assertEquals(1, traffic.verify, "One entered code must cost one attempt, not two (#400).")
        assertEquals(0, traffic.refresh, "Nothing is signed in yet, so nothing can be refreshed.")
    }

    @Test
    fun rejectedCode_costsOneRequest_evenWhenAStaleSessionIsStillStored() = runTest {
        // Signing in again on a handset that never cleared its last session is the normal case, not
        // an edge one, and it must not turn the retry back on: a stored refresh token would let the
        // plugin refresh and replay, spending a second attempt on the same six digits.
        val traffic = Traffic()
        val storage = FakeTokenStorage()
        storage.saveTokens("stale-access", "stale-refresh", "user-1", "device-1")
        val repo = AuthRepository(clientRejectingEverything(traffic, storage), storage)

        assertFailsWith<ApiException> {
            repo.verifyOtp("+905000000001", "000000", "Pixel", "ANDROID")
        }

        assertEquals(1, traffic.verify, "A leftover session must not buy the code a second attempt.")
    }

    @Test
    fun rejectedFirebaseToken_costsExactlyOneRequest() = runTest {
        // Same 401, same plugin, same replay — and the Firebase path is what the app tries first on
        // a real handset, so it doubles just as quietly.
        val traffic = Traffic()
        val storage = FakeTokenStorage()
        val repo = AuthRepository(clientRejectingEverything(traffic, storage), storage)

        assertFailsWith<ApiException> {
            repo.verifyFirebaseToken("bad-id-token", "Pixel", "ANDROID")
        }

        assertEquals(1, traffic.firebaseVerify)
    }

    @Test
    fun expiredAccessToken_onAnOrdinaryEndpoint_isStillRefreshedAndReplayed() = runTest {
        // The other half of the fix, and the reason it narrows the predicate rather than removing
        // the retry: a 401 anywhere outside the pre-login set is still a challenge, and the app
        // still has to answer it or every session ends at the first expiry.
        val traffic = Traffic()
        val storage = FakeTokenStorage()
        storage.saveTokens("expired-access", "good-refresh", "user-1", "device-1")
        var protectedCalls = 0

        val client = ApiClient(storage, MockEngine { request ->
            when {
                request.url.encodedPath == "/api/v1/auth/token/refresh" -> {
                    traffic.refresh++
                    respond(
                        """{"data":{"accessToken":"fresh-access","refreshToken":"fresh-refresh"},"timestamp":"2026-08-21T09:50:16Z"}""",
                        HttpStatusCode.OK,
                        jsonContentType,
                    )
                }

                else -> {
                    protectedCalls++
                    if (protectedCalls == 1) {
                        respond(
                            """{"error":{"code":"AUTH_TOKEN_EXPIRED","message":"Token süresi doldu"},"timestamp":"2026-08-21T09:50:16Z"}""",
                            HttpStatusCode.Unauthorized,
                            jsonContentType,
                        )
                    } else {
                        respond("""{"data":[],"timestamp":"2026-08-21T09:50:16Z"}""", HttpStatusCode.OK, jsonContentType)
                    }
                }
            }
        })

        client.get<List<String>>("/api/v1/conversations")

        assertEquals(2, protectedCalls, "An expired token must still be refreshed and the call replayed.")
        assertEquals(1, traffic.refresh)
        assertEquals("fresh-access", storage.getAccessToken())
    }
}

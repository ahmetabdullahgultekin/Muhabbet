package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ApiException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #544, at the layer where it went wrong.
 *
 * `TwoStepSetupScreen` posted the setup body to `/api/v1/auth/two-step`. The controller serves setup
 * at `/api/v1/auth/two-step/setup`; the bare path answered `DELETE` only, so Spring replied **405
 * METHOD_NOT_ALLOWED** and the screen — which caught every exception into one string — told the
 * owner "bir hata oluştu". The disable call was worse: a `DELETE` with no body at all against a
 * handler that requires the current PIN, so it could only ever have been a 400.
 *
 * So the first thing these tests assert is the method, path and body of each call. A test that only
 * checked the response shape would have passed happily against a URL nothing serves — which is
 * exactly how #392 survived, and why that test file opens with the same sentence.
 */
class TwoStepRepositoryTest {

    private class RecordedRequest(val method: String, val path: String, val body: String)

    private val recorded = mutableListOf<RecordedRequest>()

    private fun repositoryRespondingWith(
        status: HttpStatusCode,
        body: String,
    ): TwoStepRepository = TwoStepRepository(
        ApiClient(
            FakeTokenStorage().apply { saveTokens("access-1", "refresh-1", "user-1", "device-1") },
            MockEngine { request ->
                recorded += RecordedRequest(
                    method = request.method.value,
                    path = request.url.encodedPath,
                    body = request.body.toByteArray().decodeToString(),
                )
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
    )

    private val okEmpty = """{"data":null,"timestamp":"2026-08-17T10:00:00Z"}"""

    @Test
    fun status_asksThePathTheServerActuallyServes() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"enabled":true,"hasEmail":true},"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        repository.status()

        assertEquals(listOf("GET" to "/api/v1/auth/two-step/status"), recorded.map { it.method to it.path })
    }

    @Test
    fun enable_asksThePathTheServerActuallyServes() = runTest {
        // The reported bug. Before the fix this was POST /api/v1/auth/two-step, which the server
        // maps for DELETE only — 405, surfaced to the user as a generic failure.
        val repository = repositoryRespondingWith(HttpStatusCode.OK, okEmpty)

        repository.enable("123456")

        assertEquals(listOf("POST" to "/api/v1/auth/two-step/setup"), recorded.map { it.method to it.path })
    }

    @Test
    fun enable_sendsThePinTheEndpointReads() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.OK, okEmpty)

        repository.enable("123456")

        val sent = recorded.single().body
        assertTrue(sent.contains("\"pin\":\"123456\""), "pin should be sent: $sent")
    }

    @Test
    fun enable_sendsNoRecoveryAddressAtAll() = runTest {
        // The "recovery email" was collected for an endpoint that verified nothing and has been
        // removed (#566). Sending null rather than an address is the honest state: there is no
        // recovery yet, so there is nothing to store one for.
        val repository = repositoryRespondingWith(HttpStatusCode.OK, okEmpty)

        repository.enable("123456")

        assertTrue(recorded.single().body.contains("\"email\":null"), "email should be null: ${recorded.single().body}")
    }

    @Test
    fun disable_sendsTheCurrentPinTheServerRechecks() = runTest {
        // Before the fix this was a bodiless DELETE, and the handler requires the PIN: a 400 that
        // no user could ever have got past.
        val repository = repositoryRespondingWith(HttpStatusCode.OK, okEmpty)

        repository.disable("123456")

        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/two-step/disable", request.path)
        assertTrue(request.body.contains("\"currentPin\":\"123456\""), "currentPin should be sent: ${request.body}")
    }

    @Test
    fun everyCall_carriesTheBearerToken() = runTest {
        // `sendWithoutRequest` used to withhold the token from any path containing an `auth`
        // segment, which is every endpoint on this screen. `/api/v1/auth/**` is permitAll on the
        // server, so nothing bounced them at the filter chain — they reached the controller with no
        // SecurityContext and came back 401 AUTH_UNAUTHORIZED.
        val tokenStorage = FakeTokenStorage().apply { saveTokens("access-1", "refresh-1", "u-1", "d-1") }
        val seenAuthorization = mutableListOf<String?>()
        val repository = TwoStepRepository(
            ApiClient(
                tokenStorage,
                MockEngine { request ->
                    seenAuthorization += request.headers[HttpHeaders.Authorization]
                    respond(
                        content = """{"data":{"enabled":false,"hasEmail":false},"timestamp":"2026-08-17T10:00:00Z"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            )
        )

        repository.status()
        repository.enable("123456")
        repository.disable("123456")

        assertEquals(List(3) { "Bearer access-1" }, seenAuthorization.map { it.orEmpty() })
    }

    @Test
    fun status_onSuccess_reportsWhatTheServerSaid() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"enabled":true,"hasEmail":true},"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        val status = repository.status()

        assertTrue(status.enabled)
        assertTrue(status.hasEmail)
    }

    @Test
    fun status_whenTheServerHasNoRecoveryEmail_saysSo() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"enabled":true,"hasEmail":false},"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        assertFalse(repository.status().hasEmail)
    }

    @Test
    fun enable_whenAlreadyEnabled_failsWithTheCodeTheScreenTranslates() = runTest {
        // The screen maps this to "two-step is already on" rather than "an error occurred", which is
        // the whole point of #544's second half.
        val repository = repositoryRespondingWith(
            HttpStatusCode.Conflict,
            """{"error":{"code":"AUTH_2FA_ALREADY_ENABLED","message":"İki adımlı doğrulama zaten etkin"},"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.enable("123456") }

        assertEquals("AUTH_2FA_ALREADY_ENABLED", failure.code)
    }

    @Test
    fun disable_withTheWrongPin_failsInsteadOfReportingSuccess() = runTest {
        // `disable` returns Unit, so a swallowed rejection would leave the screen showing "turned
        // off" while the server still has the PIN — the exact shape of failure #374 was about.
        val repository = repositoryRespondingWith(
            HttpStatusCode.Unauthorized,
            """{"error":{"code":"AUTH_2FA_PIN_INVALID","message":"Geçersiz iki adımlı doğrulama PIN'i"},"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.disable("000000") }

        assertEquals("AUTH_2FA_PIN_INVALID", failure.code)
    }

    @Test
    fun status_whenTheEndpointIsNotThere_failsRatherThanReportingItOff() = runTest {
        // Reporting "off" for an account that has two-step on invites the user to set a second PIN
        // and then blames them for the AUTH_2FA_ALREADY_ENABLED that follows.
        val repository = repositoryRespondingWith(
            HttpStatusCode.NotFound,
            """{"error":{"code":"ENDPOINT_NOT_FOUND","message":"Böyle bir adres yok"},"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        assertFailsWith<ApiException> { repository.status() }
    }
}

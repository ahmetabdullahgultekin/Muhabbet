package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transport policy `ApiClient` installs (#822): timeouts, idempotent-only retry with backoff,
 * and a bearer token scoped to the API's own host.
 *
 * Before it, the client had no `HttpTimeout` and no `HttpRequestRetry` at all, and the bearer
 * predicate looked at the path only — so every assertion below fails on the base commit: a 503 GET
 * was tried once, a stalled request never timed out, and a request to any host carried the JWT.
 */
class ApiClientTransportPolicyTest {

    @Serializable
    private data class Thing(val id: String)

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val sleeps = mutableListOf<Long>()

    /** Real policy shape, but no jitter and a recording sleep, so backoff is observable and fast. */
    private val testPolicy = HttpTransportPolicy(
        requestTimeoutMs = 200,
        maxRetries = 3,
        retryBaseDelayMs = 100,
        retryMaxDelayMs = 1_000,
        retryJitterMs = 0,
        sleep = { sleeps += it },
    )

    private fun loggedInStorage() = FakeTokenStorage().apply {
        saveTokens(accessToken = "access-jwt", refreshToken = "refresh", userId = "u", deviceId = "d")
    }

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = ApiClient(loggedInStorage(), MockEngine(handler), testPolicy)

    @Test
    fun get_on503_isRetriedWithExponentialBackoff_thenFailsTyped() = runTest {
        var attempts = 0
        val api = client {
            attempts++
            respond("""<html>busy</html>""", HttpStatusCode.ServiceUnavailable)
        }

        val failure = assertFailsWith<ApiException> { api.get<Thing>("/api/v1/things") }

        assertEquals(1 + testPolicy.maxRetries, attempts, "one attempt plus every retry")
        assertEquals(listOf(100L, 200L, 400L), sleeps, "base·2^(n-1), no jitter in this policy")
        assertIs<DataError.Server>(failure.error)
        assertEquals(503, failure.status)
    }

    @Test
    fun get_recoversWhenARetrySucceeds() = runTest {
        var attempts = 0
        val api = client {
            attempts++
            if (attempts == 1) respond("", HttpStatusCode.BadGateway)
            else respond("""{"data":{"id":"x"},"timestamp":""}""", HttpStatusCode.OK, json)
        }

        assertEquals("x", api.get<Thing>("/api/v1/things").data?.id)
        assertEquals(2, attempts)
    }

    @Test
    fun post_on503_isNeverRetried() = runTest {
        var attempts = 0
        val api = client {
            attempts++
            respond("", HttpStatusCode.ServiceUnavailable)
        }

        assertFailsWith<ApiException> { api.post<Thing>("/api/v1/messages", mapOf("a" to "b")) }

        assertEquals(1, attempts, "the server may have acted on a POST; repeating it is a duplicate")
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun patch_isNeverRetried() = runTest {
        var attempts = 0
        val api = client {
            attempts++
            respond("", HttpStatusCode.InternalServerError)
        }

        assertFailsWith<ApiException> { api.patch<Thing>("/api/v1/messages/1", mapOf("a" to "b")) }

        assertEquals(1, attempts)
    }

    @Test
    fun clientErrors_areNotRetried() = runTest {
        var attempts = 0
        val api = client {
            attempts++
            respond("""{"error":{"code":"CONV_NOT_FOUND","message":"x"},"timestamp":""}""", HttpStatusCode.NotFound, json)
        }

        val failure = assertFailsWith<ApiException> { api.get<Thing>("/api/v1/conversations/1") }

        assertEquals(1, attempts)
        assertEquals(DataError.NotFound("CONV_NOT_FOUND"), failure.error)
    }

    @Test
    fun get_onTransportFailure_isRetried_andSurfacesAsNetwork() = runTest {
        var attempts = 0
        val api = client {
            attempts++
            throw IOException("connection reset")
        }

        val failure = assertFailsWith<DataException> { api.get<Thing>("/api/v1/things") }

        assertEquals(1 + testPolicy.maxRetries, attempts)
        assertEquals(DataError.Network, failure.error)
    }

    @Test
    fun post_onTransportFailure_isNotRetried() = runTest {
        var attempts = 0
        val api = client {
            attempts++
            throw IOException("connection reset")
        }

        val failure = assertFailsWith<DataException> { api.post<Thing>("/api/v1/messages", mapOf("a" to "b")) }

        assertEquals(1, attempts)
        assertEquals(DataError.Network, failure.error)
    }

    @Test
    fun aStalledRequest_timesOut_asATypedTimeout_andIsNotRetried() = runTest {
        var attempts = 0
        val api = client {
            attempts++
            delay(5_000)
            respond("""{"data":{"id":"late"},"timestamp":""}""", HttpStatusCode.OK, json)
        }

        val failure = assertFailsWith<DataException> { api.get<Thing>("/api/v1/things") }

        assertEquals(DataError.Timeout, failure.error)
        assertEquals(1, attempts, "the user already waited a full request timeout")
    }

    @Test
    fun theApiHost_getsTheBearerToken() = runTest {
        var authorization: String? = null
        val api = client { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond("""{"data":{"id":"x"},"timestamp":""}""", HttpStatusCode.OK, json)
        }

        api.get<Thing>("/api/v1/things")

        assertEquals("Bearer access-jwt", authorization)
    }

    @Test
    fun anotherHost_neverGetsTheBearerToken() = runTest {
        var authorization: String? = "unset"
        val api = client { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond("bytes", HttpStatusCode.OK)
        }

        api.httpClient.get("https://cdn.example.com/media/photo.jpg")

        assertNull(authorization, "a third-party host must not receive the session token")
    }

    @Test
    fun anotherHost_answering401_isNotRetriedWithTheToken() = runTest {
        val authorizations = mutableListOf<String?>()
        val api = client { request ->
            authorizations += request.headers[HttpHeaders.Authorization]
            respond("", HttpStatusCode.Unauthorized)
        }

        api.httpClient.get("https://cdn.example.com/media/photo.jpg")

        assertEquals(listOf<String?>(null), authorizations)
    }

    @Test
    fun idempotency_followsRfc9110() {
        assertTrue(HttpTransportPolicy.isIdempotent(HttpMethod.Get))
        assertTrue(HttpTransportPolicy.isIdempotent(HttpMethod.Put))
        assertTrue(HttpTransportPolicy.isIdempotent(HttpMethod.Delete))
        assertTrue(!HttpTransportPolicy.isIdempotent(HttpMethod.Post))
        assertTrue(!HttpTransportPolicy.isIdempotent(HttpMethod.Patch))
    }

    @Test
    fun backoff_isCappedAtTheMaximum() {
        assertEquals(1_000L, HttpTransportPolicy.backoffMs(testPolicy, retry = 10))
    }
}

package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What `ApiClient` does with the HTTP status it used to only log.
 *
 * `ApiResponse` has `data` and `error` both nullable, so a 4xx/5xx carrying the standard error
 * envelope deserialised **cleanly** with `data = null`. Nothing threw, and every caller written as
 * `response.data ?: emptyList()` reported a server failure as an empty screen while every caller
 * that ignored the return value reported it as success. These tests pin the four answers a response
 * can now produce: throw with the envelope's code, throw with a synthetic code when the body never
 * came from the application, return an empty envelope for a body-less 2xx, and decode as before.
 */
class ApiClientStatusTest {

    @Serializable
    private data class Thing(val id: String)

    private val jsonContentType =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun clientRespondingWith(
        status: HttpStatusCode,
        body: String,
        contentType: ContentType = ContentType.Application.Json,
    ): ApiClient = ApiClient(
        FakeTokenStorage(),
        MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, contentType.toString()),
            )
        },
    )

    @Test
    fun forbidden_withErrorEnvelope_throwsInsteadOfDecodingToASuccess() = runTest {
        val client = clientRespondingWith(
            HttpStatusCode.Forbidden,
            """{"error":{"code":"GROUP_PERMISSION_DENIED","message":"Bu işlem için yetkiniz yok"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { client.get<List<Thing>>("/api/v1/communities") }

        assertEquals(403, failure.status)
        assertEquals("GROUP_PERMISSION_DENIED", failure.code)
        assertEquals("Bu işlem için yetkiniz yok", failure.message)
    }

    @Test
    fun serverError_withErrorEnvelope_preservesTheErrorCode() = runTest {
        val client = clientRespondingWith(
            HttpStatusCode.InternalServerError,
            """{"error":{"code":"MEDIA_UPLOAD_FAILED","message":"Dosya yükleme başarısız"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { client.post<Thing>("/api/v1/media", mapOf("a" to "b")) }

        assertEquals(500, failure.status)
        // The code is what a screen maps to a message, so losing it would leave the UI with nothing
        // but an untranslated server string to show.
        assertEquals("MEDIA_UPLOAD_FAILED", failure.code)
    }

    @Test
    fun badGateway_withHtmlBody_throwsApiExceptionAndNotASerializationFailure() = runTest {
        // A Traefik error page: the request never reached the application, so there is no envelope
        // and no ErrorCode. Decoding it as one is a SerializationException, which reads to a caller
        // as "the server sent something malformed" rather than "the server was unreachable".
        val client = clientRespondingWith(
            HttpStatusCode.BadGateway,
            "<html><head><title>502 Bad Gateway</title></head><body>Bad Gateway</body></html>",
            contentType = ContentType.Text.Html,
        )

        val failure = assertFailsWith<ApiException> { client.get<Thing>("/api/v1/communities") }

        assertEquals(502, failure.status)
        assertEquals("HTTP_502", failure.code)
        assertTrue(failure.message.contains("502 Bad Gateway"), "raw body should survive: ${failure.message}")
    }

    @Test
    fun emptyBody_onNoContent_doesNotThrow() = runTest {
        // Several `delete<Unit>` call sites depend on this: decodeFromString("") throws, which would
        // have turned every successful delete into a reported failure.
        val client = clientRespondingWith(HttpStatusCode.NoContent, "")

        val response = client.delete<Unit>("/api/v1/starred/abc")

        assertNull(response.data)
        assertNull(response.error)
    }

    @Test
    fun emptyBody_onOk_doesNotThrow() = runTest {
        val client = clientRespondingWith(HttpStatusCode.OK, "")

        assertNull(client.put<Unit>("/api/v1/conversations/abc/pin", Unit).data)
    }

    @Test
    fun ok_withEnvelope_stillDecodesUnchanged() = runTest {
        val client = clientRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"id":"thing-1"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val response = client.get<Thing>("/api/v1/things/thing-1")

        assertEquals("thing-1", response.data?.id)
        assertNull(response.error)
    }

    @Test
    fun ok_withListPayload_stillDecodesUnchanged() = runTest {
        val client = clientRespondingWith(
            HttpStatusCode.OK,
            """{"data":[{"id":"a"},{"id":"b"}],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        assertEquals(listOf("a", "b"), client.get<List<Thing>>("/api/v1/things").data?.map { it.id })
    }

    @Test
    fun unauthorized_thatRefreshRecoversFrom_succeeds() = runTest {
        // The Ktor Auth plugin retries the original request after refreshing, and the status check
        // sees only the final response — so a recovered 401 must not throw. Guarding this because
        // the obvious way to write the fix (check the status inside a send interceptor) breaks it.
        val tokenStorage = FakeTokenStorage()
        tokenStorage.saveTokens("expired-access", "good-refresh", "user-1", "device-1")
        var protectedCalls = 0

        val client = ApiClient(
            tokenStorage,
            MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/auth/token/refresh") -> respond(
                        content = """{"data":{"accessToken":"fresh-access","refreshToken":"fresh-refresh"},"timestamp":"2026-08-15T10:00:00Z"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonContentType,
                    )

                    else -> {
                        protectedCalls++
                        if (protectedCalls == 1) {
                            respond(
                                content = """{"error":{"code":"AUTH_TOKEN_EXPIRED","message":"Token süresi doldu"},"timestamp":"2026-08-15T10:00:00Z"}""",
                                status = HttpStatusCode.Unauthorized,
                                headers = jsonContentType,
                            )
                        } else {
                            respond(
                                content = """{"data":{"id":"thing-1"},"timestamp":"2026-08-15T10:00:00Z"}""",
                                status = HttpStatusCode.OK,
                                headers = jsonContentType,
                            )
                        }
                    }
                }
            },
        )

        assertEquals("thing-1", client.get<Thing>("/api/v1/things/thing-1").data?.id)
        assertEquals("fresh-access", tokenStorage.getAccessToken())
    }

    @Test
    fun unauthorized_thatRefreshCannotRecover_throws() = runTest {
        val tokenStorage = FakeTokenStorage()
        tokenStorage.saveTokens("expired-access", "dead-refresh", "user-1", "device-1")

        val client = ApiClient(
            tokenStorage,
            MockEngine {
                respond(
                    content = """{"error":{"code":"AUTH_TOKEN_EXPIRED","message":"Token süresi doldu"},"timestamp":"2026-08-15T10:00:00Z"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonContentType,
                )
            },
        )

        val failure = assertFailsWith<ApiException> { client.get<Thing>("/api/v1/things/thing-1") }

        assertEquals(401, failure.status)
        assertEquals("AUTH_TOKEN_EXPIRED", failure.code)
    }
}

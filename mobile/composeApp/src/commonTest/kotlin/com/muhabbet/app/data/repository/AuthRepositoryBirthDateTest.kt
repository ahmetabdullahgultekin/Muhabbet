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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The age gate's two calls (#751): the date of birth riding on sign-in, and the one-time declaration
 * for an account from before the gate. Method, path and body first, because a call to a path nothing
 * serves would pass any test that only looked at the response.
 */
class AuthRepositoryBirthDateTest {

    private class RecordedRequest(val method: String, val path: String, val body: String)

    private val recorded = mutableListOf<RecordedRequest>()

    private fun repositoryRespondingWith(status: HttpStatusCode, body: String): AuthRepository {
        val tokenStorage = FakeTokenStorage().apply { saveTokens("access-1", "refresh-1", "user-1", "device-1") }
        val apiClient = ApiClient(
            tokenStorage,
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
        return AuthRepository(apiClient, tokenStorage)
    }

    private val tokens = """
        {"data":{"accessToken":"a","refreshToken":"r","expiresIn":900,"userId":"u","deviceId":"d",
        "isNewUser":false,"ageVerificationRequired":true},"timestamp":"2026-11-12T10:00:00Z"}
    """.trimIndent()

    @Test
    fun should_send_iso_birth_date_when_verifying_otp_with_one() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.OK, tokens)

        repository.verifyOtp("+905000000001", "123456", "Pixel", "android", birthDate = LocalDate(2001, 3, 9))

        val request = recorded.single()
        assertEquals("/api/v1/auth/otp/verify", request.path)
        assertTrue(request.body.contains("\"birthDate\":\"2001-03-09\""), request.body)
    }

    @Test
    fun should_send_birth_date_when_verifying_firebase_token_with_one() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.OK, tokens)

        repository.verifyFirebaseToken("id-token", "Pixel", "android", birthDate = LocalDate(1990, 12, 31))

        val request = recorded.single()
        assertEquals("/api/v1/auth/firebase-verify", request.path)
        assertTrue(request.body.contains("\"birthDate\":\"1990-12-31\""), request.body)
    }

    @Test
    fun should_report_age_verification_required_when_server_flags_the_account() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.OK, tokens)

        val result = repository.verifyOtp("+905000000001", "123456", "Pixel", "android")

        assertTrue(result.ageVerificationRequired)
    }

    @Test
    fun should_patch_the_birth_date_path_when_declaring() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"ageVerificationRequired":false},"timestamp":"2026-11-12T10:00:00Z"}""",
        )

        val status = repository.declareBirthDate(LocalDate(1990, 5, 1))

        val request = recorded.single()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/users/me/birth-date", request.path)
        assertEquals("""{"birthDate":"1990-05-01"}""", request.body)
        assertFalse(status.ageVerificationRequired)
    }

    @Test
    fun should_surface_the_error_code_when_declaration_is_refused_below_minimum() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.Forbidden,
            """{"error":{"code":"AUTH_AGE_BELOW_MINIMUM","message":"x"},"timestamp":"2026-11-12T10:00:00Z"}""",
        )

        val thrown = assertFailsWith<ApiException> { repository.declareBirthDate(LocalDate(2015, 1, 1)) }

        assertEquals("AUTH_AGE_BELOW_MINIMUM", thrown.code)
    }
}

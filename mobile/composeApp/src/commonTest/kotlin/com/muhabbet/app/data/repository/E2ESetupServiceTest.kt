package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.port.NoOpKeyManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That placeholder key material never reaches the server.
 *
 * `registerKeys()` used to run on every launch for every logged-in user, gated on nothing but
 * `isLoggedIn()`. `NoOpKeyManager` is wired on both platforms while libsignal is blocked and holds
 * its identity key in a plain field, so each fresh process minted a new
 * `noop-identity-key-<random>` and PUT it to production, followed by 100 placeholder pre-keys. At
 * the database level that is indistinguishable from real X3DH material.
 *
 * The test asserts on requests actually issued rather than on a return value, because the defect
 * was never visible in what the method returned.
 */
class E2ESetupServiceTest {

    /** Records every request the client issues, and fails the call if one is made. */
    private class RecordingEngine {
        val paths = mutableListOf<String>()
        fun engine() = MockEngine { request ->
            paths += request.url.encodedPath
            respond(
                content = """{"data":null,"timestamp":"2026-08-15T10:00:00Z"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    }

    @Test
    fun should_issue_no_requests_when_the_key_manager_produces_placeholder_material() = runTest {
        val recorder = RecordingEngine()
        val service = E2ESetupService(
            keyManager = NoOpKeyManager(),
            encryptionRepository = EncryptionRepository(ApiClient(FakeTokenStorage(), recorder.engine())),
        )

        service.registerKeys()

        assertTrue(
            recorder.paths.isEmpty(),
            "registerKeys() published placeholder key material to ${recorder.paths}. " +
                "NoOpKeyManager must never reach the network.",
        )
    }

    @Test
    fun should_declare_that_the_placeholder_manager_is_not_real() {
        // The guard reads this property rather than sniffing the key strings, so that a future
        // placeholder implementation cannot inherit "these keys are real" by staying silent.
        assertEquals(false, NoOpKeyManager().producesRealKeyMaterial)
    }
}

package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** #787: a status view reaches the server, once, and a failed report is retried next time. */
class StatusViewRecorderTest {

    private val recorded = mutableListOf<Pair<String, String>>()

    private fun recorder(scope: TestScope, status: () -> HttpStatusCode) = StatusViewRecorder(
        ApiClient(
            FakeTokenStorage().apply { saveTokens("access-1", "refresh-1", "user-1", "device-1") },
            MockEngine { request ->
                recorded += request.method.value to request.url.encodedPath
                val code = status()
                val body = if (code.value < 300) {
                    """{"data":null,"timestamp":"2026-11-13T10:00:00Z"}"""
                } else {
                    """{"error":{"code":"STATUS_NOT_FOUND","message":"x"},"timestamp":"2026-11-13T10:00:00Z"}"""
                }
                respond(body, code, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        ),
        scope
    )

    @Test
    fun markViewed_postsTheViewOnce_andRemembersItLocally() = runTest(StandardTestDispatcher()) {
        val recorder = recorder(this) { HttpStatusCode.OK }

        recorder.markViewed("s1")?.join()
        // Already reported: no second request, nothing to wait for.
        assertEquals(null, recorder.markViewed("s1"))

        assertEquals(listOf("POST" to "/api/v1/statuses/s1/views"), recorded)
        assertEquals(setOf("s1"), recorder.viewed.value)
    }

    @Test
    fun markViewed_forgetsAFailedReport_soTheNextViewingRetries() = runTest(StandardTestDispatcher()) {
        var code = HttpStatusCode.InternalServerError
        val recorder = recorder(this) { code }

        recorder.markViewed("s1")?.join()
        assertEquals(emptySet(), recorder.viewed.value)

        code = HttpStatusCode.OK
        recorder.markViewed("s1")?.join()

        assertEquals(2, recorded.size)
        assertEquals(setOf("s1"), recorder.viewed.value)
    }
}

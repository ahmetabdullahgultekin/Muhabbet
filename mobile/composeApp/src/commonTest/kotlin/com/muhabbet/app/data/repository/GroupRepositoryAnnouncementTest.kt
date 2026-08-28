package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ApiException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #509, at the layer where it went wrong.
 *
 * The switch on `GroupInfoScreen` built its own call: `PATCH /api/v1/conversations/{id}` carrying
 * `{"announcementOnly": true}`. The server binds that route to `UpdateGroupRequest`, which has no
 * such field, and `ignoreUnknownKeys` discarded it behind a 200 — so the group stayed open to
 * everyone while the UI said otherwise. Nothing in either half was wrong on its own; only the pair
 * was, which is why the assertions here are about the **request** and not just the answer.
 */
class GroupRepositoryAnnouncementTest {

    private class Recorder {
        var request: HttpRequestData? = null
    }

    private fun repositoryRespondingWith(
        status: HttpStatusCode,
        body: String,
        recorder: Recorder = Recorder(),
    ): Pair<GroupRepository, Recorder> {
        val repository = GroupRepository(
            ApiClient(
                FakeTokenStorage(),
                MockEngine { request ->
                    recorder.request = request
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            )
        )
        return repository to recorder
    }

    private fun bodyText(request: HttpRequestData?): String =
        (request?.body as? TextContent)?.text ?: ""

    @Test
    fun setAnnouncementMode_putsToTheAnnouncementRoute_notTheUpdateGroupRoute() = runTest {
        val (repository, recorder) = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"announcementOnly":true},"timestamp":"2026-08-16T10:00:00Z"}""",
        )

        repository.setAnnouncementMode("conv-1", enabled = true)

        val request = recorder.request
        assertEquals(HttpMethod.Put, request?.method)
        assertEquals("/api/v1/conversations/conv-1/announcement", request?.url?.encodedPath)
    }

    /**
     * The field name is the whole bug. `enabled` is what the server reads; `announcementOnly` in the
     * body is what the broken client sent, and what the server threw away.
     */
    @Test
    fun setAnnouncementMode_sendsTheFieldTheServerActuallyBinds() = runTest {
        val (repository, recorder) = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"announcementOnly":true},"timestamp":"2026-08-16T10:00:00Z"}""",
        )

        repository.setAnnouncementMode("conv-1", enabled = true)

        assertEquals("""{"enabled":true}""", bodyText(recorder.request))
    }

    @Test
    fun setAnnouncementMode_returnsWhatTheServerStored_notTheArgument() = runTest {
        // The server says it is still off. A caller that trusted its own argument would light the
        // switch anyway — which is the failure this endpoint exists to make impossible.
        val (repository, _) = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"announcementOnly":false},"timestamp":"2026-08-16T10:00:00Z"}""",
        )

        assertFalse(repository.setAnnouncementMode("conv-1", enabled = true))
    }

    @Test
    fun setAnnouncementMode_returnsTrueWhenTheServerStoredIt() = runTest {
        val (repository, _) = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"announcementOnly":true},"timestamp":"2026-08-16T10:00:00Z"}""",
        )

        assertTrue(repository.setAnnouncementMode("conv-1", enabled = true))
    }

    /**
     * A member who is not an admin gets 403 from the route. It has to surface as a failure so the
     * screen can leave the switch where it was and say so — the old path could not distinguish this
     * from success at all.
     */
    @Test
    fun setAnnouncementMode_onPermissionDenied_fails() = runTest {
        val (repository, _) = repositoryRespondingWith(
            HttpStatusCode.Forbidden,
            """{"error":{"code":"GROUP_PERMISSION_DENIED","message":"Yetkiniz yok"},"timestamp":"2026-08-16T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.setAnnouncementMode("conv-1", enabled = true) }

        assertEquals(403, failure.status)
        assertEquals("GROUP_PERMISSION_DENIED", failure.code)
    }
}

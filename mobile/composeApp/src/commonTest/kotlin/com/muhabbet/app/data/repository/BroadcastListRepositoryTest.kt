package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ApiException
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
import kotlin.test.assertTrue

/**
 * #392, at the layer where it went wrong.
 *
 * Both broadcast screens asked for `/api/v1/broadcasts`; the controller is mapped to
 * `/api/v1/broadcast-lists`. Every request had 404'd since the feature shipped, and before #374 a
 * 404 decoded to `data = null` and rendered as "you have no broadcast lists".
 *
 * So the first thing these tests assert is the request path itself — a shape test alone would have
 * passed happily against a URL nothing serves. The rest pin the response shape, which had never
 * been exercised even once.
 */
class BroadcastListRepositoryTest {

    private val requestedPaths = mutableListOf<String>()

    private fun repositoryRespondingWith(
        status: HttpStatusCode,
        body: String,
    ): BroadcastListRepository = BroadcastListRepository(
        ApiClient(
            FakeTokenStorage(),
            MockEngine { request ->
                requestedPaths += request.url.encodedPath
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
    )

    @Test
    fun getBroadcastLists_asksThePathTheServerActuallyServes() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        repository.getBroadcastLists()

        assertEquals(listOf("/api/v1/broadcast-lists"), requestedPaths)
    }

    @Test
    fun getBroadcastListMembers_asksThePathTheServerActuallyServes() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        repository.getBroadcastListMembers("b-1")

        assertEquals(listOf("/api/v1/broadcast-lists/b-1/members"), requestedPaths)
    }

    @Test
    fun createBroadcastList_asksThePathTheServerActuallyServes() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.Created,
            """{"data":{"id":"b-1","name":"Aile","memberCount":0,"createdAt":"2026-08-15T09:00:00Z"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        repository.createBroadcastList("Aile")

        assertEquals(listOf("/api/v1/broadcast-lists"), requestedPaths)
    }

    @Test
    fun getBroadcastLists_onSuccess_decodesEveryFieldTheRowRenders() = runTest {
        // memberCount is the field the old controller never sent. The client's default is 0, so its
        // absence rendered "0 üye" on every row instead of failing — this is what pins it.
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[{"id":"b-1","name":"Aile","memberCount":7,"createdAt":"2026-08-15T09:00:00Z"}],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val list = repository.getBroadcastLists().single()

        assertEquals("b-1", list.id)
        assertEquals("Aile", list.name)
        assertEquals(7, list.memberCount)
        assertEquals("2026-08-15T09:00:00Z", list.createdAt)
    }

    @Test
    fun getBroadcastLists_onNotFound_failsInsteadOfRenderingAnEmptyState() = runTest {
        // The exact shape of the bug: the wrong path 404'd, and the screen said "no broadcast
        // lists". Since #374 the status is checked, so a 404 must reach the caller as a failure.
        val repository = repositoryRespondingWith(
            HttpStatusCode.NotFound,
            """{"error":{"code":"NOT_FOUND","message":"Bulunamadı"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.getBroadcastLists() }

        assertEquals(404, failure.status)
    }

    @Test
    fun getBroadcastLists_onEmptySuccess_stillReturnsAnEmptyList() = runTest {
        // The other half: an owner with no lists must keep seeing the empty state, not an error.
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        assertTrue(repository.getBroadcastLists().isEmpty())
    }

    @Test
    fun getBroadcastListMembers_onSuccess_decodesTheNameAndAvatarTheRowRenders() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[{"userId":"u-1","displayName":"Ayşe","avatarUrl":"https://cdn.example/a.jpg"}],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val member = repository.getBroadcastListMembers("b-1").single()

        assertEquals("u-1", member.userId)
        assertEquals("Ayşe", member.displayName)
        assertEquals("https://cdn.example/a.jpg", member.avatarUrl)
    }

    @Test
    fun getBroadcastListMembers_whenTheRecipientHasNoProfile_decodesWithNulls() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[{"userId":"u-1"}],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val member = repository.getBroadcastListMembers("b-1").single()

        assertEquals("u-1", member.userId)
        assertNull(member.displayName)
        assertNull(member.avatarUrl)
    }

    @Test
    fun getBroadcastListMembers_whenCallerIsNotTheOwner_failsInsteadOfShowingNoRecipients() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.NotFound,
            """{"error":{"code":"BROADCAST_LIST_NOT_FOUND","message":"Yayın listesi bulunamadı"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.getBroadcastListMembers("b-1") }

        assertEquals("BROADCAST_LIST_NOT_FOUND", failure.code)
    }

    @Test
    fun createBroadcastList_onSuccess_returnsTheCreatedList() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.Created,
            """{"data":{"id":"b-1","name":"Aile","memberCount":0,"createdAt":"2026-08-15T09:00:00Z"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val created = repository.createBroadcastList("Aile")

        assertEquals("b-1", created.id)
        assertEquals("Aile", created.name)
        assertEquals(0, created.memberCount)
    }

    @Test
    fun createBroadcastList_whenRejected_failsInsteadOfAddingAPhantomRow() = runTest {
        // The screen appends the returned list to what it is showing. A swallowed failure put a row
        // on screen that does not exist on the server and vanishes on the next refresh.
        val repository = repositoryRespondingWith(
            HttpStatusCode.InternalServerError,
            """{"error":{"code":"INTERNAL_ERROR","message":"Beklenmeyen hata"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.createBroadcastList("Aile") }

        assertEquals(500, failure.status)
    }
}

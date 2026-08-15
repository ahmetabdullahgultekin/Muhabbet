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
import kotlin.test.assertTrue

/**
 * The symptom that opened #374, at the layer where a user would have seen it.
 *
 * `getCommunities()` reads `response.data ?: emptyList()`. Because the error envelope decoded
 * cleanly with `data = null`, a 500 produced an empty list and the screen rendered "Henüz topluluk
 * yok" — precisely the lie the comment above that code exists to prevent.
 */
class CommunityRepositoryTest {

    private fun repositoryRespondingWith(status: HttpStatusCode, body: String): CommunityRepository =
        CommunityRepository(
            ApiClient(
                FakeTokenStorage(),
                MockEngine {
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            )
        )

    @Test
    fun getCommunities_onServerError_failsInsteadOfReturningAnEmptyList() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.InternalServerError,
            """{"error":{"code":"INTERNAL_ERROR","message":"Beklenmeyen hata"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.getCommunities() }

        assertEquals(500, failure.status)
        assertEquals("INTERNAL_ERROR", failure.code)
    }

    @Test
    fun getCommunities_onEmptySuccess_stillReturnsAnEmptyList() = runTest {
        // The other half of the fix: a genuinely empty account must keep rendering the empty state,
        // not an error. Only the status distinguishes the two, which is the whole point.
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        assertTrue(repository.getCommunities().isEmpty())
    }

    @Test
    fun getCommunities_onSuccess_returnsTheCommunities() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[{"id":"c-1","name":"Mahalle","memberCount":3,"groupCount":2,"createdAt":"2026-08-15T09:00:00Z"}],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        assertEquals(listOf("Mahalle"), repository.getCommunities().map { it.name })
    }

    @Test
    fun addGroupToCommunity_whenRejected_failsInsteadOfReportingSuccess() = runTest {
        // `AddGroupToCommunitySheet` showed "Grup topluluğa eklendi" after a 403, because the call
        // discarded its result and nothing else could tell it the request had been refused.
        val repository = repositoryRespondingWith(
            HttpStatusCode.Forbidden,
            """{"error":{"code":"GROUP_PERMISSION_DENIED","message":"Bu işlem için yetkiniz yok"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.addGroupToCommunity("c-1", "conv-1") }

        assertEquals("GROUP_PERMISSION_DENIED", failure.code)
    }

    @Test
    fun removeGroupFromCommunity_onNoContent_succeeds() = runTest {
        // The delete endpoints answer 204 with no body; treating that as a decode failure would
        // have replaced one wrong answer with another.
        repositoryRespondingWith(HttpStatusCode.NoContent, "")
            .removeGroupFromCommunity("c-1", "conv-1")
    }
}

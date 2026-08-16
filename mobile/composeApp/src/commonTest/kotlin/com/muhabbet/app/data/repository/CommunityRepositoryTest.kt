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

    @Test
    fun getCommunityMembers_onSuccess_decodesEveryFieldTheScreenRenders() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[{"userId":"u-1","displayName":"Ayşe","avatarUrl":"https://cdn.example/a.jpg","role":"OWNER","joinedAt":"2026-01-01T00:00:00Z"}],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val member = repository.getCommunityMembers("c-1").single()

        assertEquals("u-1", member.userId)
        assertEquals("Ayşe", member.displayName)
        assertEquals("https://cdn.example/a.jpg", member.avatarUrl)
        assertEquals("OWNER", member.role)
    }

    @Test
    fun getCommunityMembers_whenCallerIsNotAMember_failsInsteadOfShowingAnEmptyCommunity() = runTest {
        // Reads are members-only since #375. Swallowing the 403 into an empty list would render a
        // community with nobody in it, which is indistinguishable from having been removed from it.
        val repository = repositoryRespondingWith(
            HttpStatusCode.Forbidden,
            """{"error":{"code":"COMMUNITY_PERMISSION_DENIED","message":"Bu topluluk işlemi için yetkiniz yok"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.getCommunityMembers("c-1") }

        assertEquals("COMMUNITY_PERMISSION_DENIED", failure.code)
    }

    @Test
    fun getAddableUsers_onSuccess_returnsTheCandidates() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[{"userId":"u-2","displayName":"Mehmet"}],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val candidate = repository.getAddableUsers("c-1").single()

        assertEquals("u-2", candidate.userId)
        assertEquals("Mehmet", candidate.displayName)
        assertNull(candidate.avatarUrl)
    }

    @Test
    fun getAddableUsers_onEmptySuccess_returnsAnEmptyList() = runTest {
        // A community whose groups everyone already belongs to is the normal state while the invite
        // flow (#387) does not exist, so the picker must be able to tell that apart from an error.
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[],"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        assertTrue(repository.getAddableUsers("c-1").isEmpty())
    }

    @Test
    fun addMemberToCommunity_whenTargetIsInNoGroup_failsInsteadOfReportingSuccess() = runTest {
        // The picker is built from the server's own candidate list, so this should not normally
        // happen — but a stale sheet must surface the refusal rather than claim the add worked.
        val repository = repositoryRespondingWith(
            HttpStatusCode.Forbidden,
            """{"error":{"code":"COMMUNITY_MEMBER_NOT_IN_ANY_GROUP","message":"Yalnızca topluluğun gruplarında bulunan kullanıcılar topluluğa eklenebilir"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.addMemberToCommunity("c-1", "u-2") }

        assertEquals("COMMUNITY_MEMBER_NOT_IN_ANY_GROUP", failure.code)
    }

    @Test
    fun addMemberToCommunity_onSuccess_succeeds() = runTest {
        repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":null,"timestamp":"2026-08-15T10:00:00Z"}""",
        ).addMemberToCommunity("c-1", "u-2")
    }

    @Test
    fun updateCommunity_onSuccess_returnsTheRenamedCommunity() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"id":"c-1","name":"Yeni Mahalle","description":"Yeni açıklama","memberCount":3,"groupCount":2,"createdAt":"2026-08-15T09:00:00Z"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val updated = repository.updateCommunity("c-1", "Yeni Mahalle", "Yeni açıklama")

        assertEquals("Yeni Mahalle", updated.name)
        assertEquals("Yeni açıklama", updated.description)
        // The counts come back so the list row the caller re-renders does not lose them.
        assertEquals(2, updated.groupCount)
        assertEquals(3, updated.memberCount)
    }

    @Test
    fun updateCommunity_whenCallerIsAPlainMember_failsInsteadOfReportingSuccess() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.Forbidden,
            """{"error":{"code":"COMMUNITY_PERMISSION_DENIED","message":"Bu topluluk işlemi için yetkiniz yok"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.updateCommunity("c-1", "Mahalle", null) }

        assertEquals(403, failure.status)
        assertEquals("COMMUNITY_PERMISSION_DENIED", failure.code)
    }

    @Test
    fun leaveCommunity_onSuccess_succeeds() = runTest {
        repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":null,"timestamp":"2026-08-15T10:00:00Z"}""",
        ).leaveCommunity("c-1")
    }

    @Test
    fun leaveCommunity_whenCallerIsTheLastMember_failsSoTheScreenCanSayWhy() = runTest {
        // The server refuses rather than strand an unreachable community. The screen must keep the
        // user on it and report, not navigate back as though they had left.
        val repository = repositoryRespondingWith(
            HttpStatusCode.BadRequest,
            """{"error":{"code":"COMMUNITY_LAST_MEMBER_CANNOT_LEAVE","message":"Topluluğun tek üyesi ayrılamaz, önce başka birini ekleyin"},"timestamp":"2026-08-15T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.leaveCommunity("c-1") }

        assertEquals("COMMUNITY_LAST_MEMBER_CANNOT_LEAVE", failure.code)
    }
}

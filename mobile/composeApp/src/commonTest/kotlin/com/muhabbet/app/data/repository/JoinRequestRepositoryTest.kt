package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.shared.model.MemberRole
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
import kotlin.test.assertTrue

/**
 * #788: the admin calls that had no client — join-request list/approve/reject, and the role change
 * whose repository method nobody called. Method and path first, because a test that only checks
 * how a mocked 200 decodes passes against a URL nothing serves.
 */
class JoinRequestRepositoryTest {

    private class Recorded(val method: String, val path: String, val body: String)

    private val recorded = mutableListOf<Recorded>()

    private fun client(status: HttpStatusCode, body: String) = ApiClient(
        FakeTokenStorage().apply { saveTokens("access-1", "refresh-1", "user-1", "device-1") },
        MockEngine { request ->
            recorded += Recorded(request.method.value, request.url.encodedPath, request.body.toByteArray().decodeToString())
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
    )

    private val okEmpty = """{"data":null,"timestamp":"2026-11-13T10:00:00Z"}"""

    @Test
    fun pending_readsTheGroupsJoinRequestsWithTheRequestersName() = runTest {
        val repository = JoinRequestRepository(
            client(
                HttpStatusCode.OK,
                """{"data":[{"id":"r1","conversationId":"g1","userId":"u9","status":"PENDING",
                   "createdAt":"2026-11-13T09:00:00Z","reviewedAt":null,"displayName":"Ayşe","avatarUrl":null}],
                   "timestamp":"2026-11-13T10:00:00Z"}"""
            )
        )

        val pending = repository.pending("g1")

        assertEquals(listOf("GET" to "/api/v1/conversations/g1/join-requests"), recorded.map { it.method to it.path })
        assertEquals("Ayşe", pending.single().displayName)
        assertEquals("r1", pending.single().id)
    }

    @Test
    fun approveAndReject_postToTheRequestsOwnPaths() = runTest {
        val repository = JoinRequestRepository(client(HttpStatusCode.OK, okEmpty))

        repository.approve("g1", "r1")
        repository.reject("g1", "r2")

        assertEquals(
            listOf(
                "POST" to "/api/v1/conversations/g1/join-requests/r1/approve",
                "POST" to "/api/v1/conversations/g1/join-requests/r2/reject",
            ),
            recorded.map { it.method to it.path }
        )
    }

    @Test
    fun pending_surfacesTheRefusal_whenTheCallerIsNotAnAdmin() = runTest {
        val repository = JoinRequestRepository(
            client(
                HttpStatusCode.Forbidden,
                """{"error":{"code":"GROUP_PERMISSION_DENIED","message":"x"},"timestamp":"2026-11-13T10:00:00Z"}"""
            )
        )

        val failure = assertFailsWith<ApiException> { repository.pending("g1") }
        assertEquals("GROUP_PERMISSION_DENIED", failure.code)
    }

    @Test
    fun updateMemberRole_patchesTheMembersRolePathWithTheNewRole() = runTest {
        val repository = GroupRepository(client(HttpStatusCode.OK, okEmpty))

        repository.updateMemberRole("g1", "u2", MemberRole.ADMIN)

        assertEquals(listOf("PATCH" to "/api/v1/conversations/g1/members/u2/role"), recorded.map { it.method to it.path })
        assertTrue(recorded.single().body.contains("\"ADMIN\""), recorded.single().body)
    }

    @Test
    fun updateMemberRole_fails_whenTheServerRefuses() = runTest {
        // A refused role change must reach the screen, which reports it, rather than leaving the
        // badge claiming a role the server never stored.
        val repository = GroupRepository(
            client(
                HttpStatusCode.Forbidden,
                """{"error":{"code":"GROUP_PERMISSION_DENIED","message":"x"},"timestamp":"2026-11-13T10:00:00Z"}"""
            )
        )

        assertFailsWith<ApiException> { repository.updateMemberRole("g1", "u2", MemberRole.ADMIN) }
    }
}

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #613, at the layer where the app's block/report actions turned out not to exist.
 *
 * Both "Block" and "Report" on `UserProfileScreen` showed a success snackbar without calling
 * anything — `onConfirm` ran straight to `snackbarHostState.showSnackbar(...)`. This is the
 * repository that now sits between the button and the server, and — same lesson as
 * `TwoStepRepositoryTest` — the first thing worth asserting is the method and the path, not just
 * the response shape. A test that only checks how a mocked 200 decodes would pass against a URL
 * nothing serves.
 */
class ModerationRepositoryTest {

    private class RecordedRequest(val method: String, val path: String, val body: String)

    private val recorded = mutableListOf<RecordedRequest>()

    private fun repositoryRespondingWith(
        status: HttpStatusCode,
        body: String,
    ): ModerationRepository = ModerationRepository(
        ApiClient(
            FakeTokenStorage().apply { saveTokens("access-1", "refresh-1", "user-1", "device-1") },
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
    )

    private val okEmpty = """{"data":null,"timestamp":"2026-08-17T10:00:00Z"}"""

    @Test
    fun blockUser_postsToTheBlocksPathWithTheTargetIdInTheUrl() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.OK, okEmpty)

        repository.blockUser("target-1")

        assertEquals(listOf("POST" to "/api/v1/moderation/blocks/target-1"), recorded.map { it.method to it.path })
    }

    @Test
    fun blockUser_whenAlreadyBlockingSelf_failsRatherThanReportingSuccess() = runTest {
        // Before this repository existed, nothing checked the response at all — the dialog closed
        // and the snackbar said "blocked" no matter what the server answered.
        val repository = repositoryRespondingWith(
            HttpStatusCode.BadRequest,
            """{"error":{"code":"BLOCK_SELF","message":"Kendinizi engelleyemezsiniz"},"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repository.blockUser("me") }

        assertEquals("BLOCK_SELF", failure.code)
    }

    @Test
    fun unblockUser_deletesTheBlocksPathWithTheTargetIdInTheUrl() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.OK, okEmpty)

        repository.unblockUser("target-1")

        assertEquals(listOf("DELETE" to "/api/v1/moderation/blocks/target-1"), recorded.map { it.method to it.path })
    }

    @Test
    fun isBlocked_asksTheCheckEndpointAndReadsTheBooleanBack() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":{"blocked":true},"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        val blocked = repository.isBlocked("target-1")

        assertTrue(blocked)
        assertEquals(listOf("GET" to "/api/v1/moderation/blocks/target-1"), recorded.map { it.method to it.path })
    }

    @Test
    fun isBlocked_defaultsToFalseOnAnEmptyBody_ratherThanThrowing() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.OK, okEmpty)

        assertFalse(repository.isBlocked("target-1"))
    }

    @Test
    fun getBlockedUsers_asksTheBareBlocksPath() = runTest {
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[],"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        repository.getBlockedUsers()

        assertEquals(listOf("GET" to "/api/v1/moderation/blocks"), recorded.map { it.method to it.path })
    }

    @Test
    fun getBlockedUsers_reportsWhatTheServerResolved_nameAndFaceIncluded() = runTest {
        // This is the point of the enrichment on the backend: the client never has to resolve a
        // UUID to a name itself, and could not — GET /users/{id} withholds a foreign user's phone
        // number, so there would be nothing to key a local contact-name map on anyway.
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            """{"data":[{"userId":"u-1","displayName":"Ada","avatarUrl":"https://cdn/a.jpg","blockedAt":"2026-08-17T09:00:00Z"}],"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        val blocked = repository.getBlockedUsers()

        assertEquals(1, blocked.size)
        assertEquals("u-1", blocked[0].userId)
        assertEquals("Ada", blocked[0].displayName)
        assertEquals("https://cdn/a.jpg", blocked[0].avatarUrl)
    }

    @Test
    fun getBlockedUsers_onAnEmptyBody_reportsNobodyRatherThanThrowing() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.OK, okEmpty)

        assertTrue(repository.getBlockedUsers().isEmpty())
    }

    @Test
    fun getBlockedUsers_whenTheServerRejects_throwsRatherThanReportingAnEmptyList() = runTest {
        // An empty list and "the server said no" must not look the same, or a 401/500 renders as
        // "you haven't blocked anyone" — the #374 shape this whole class exists to avoid repeating.
        val repository = repositoryRespondingWith(
            HttpStatusCode.Unauthorized,
            """{"error":{"code":"AUTH_UNAUTHORIZED","message":"Yetkisiz"},"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        assertFailsWith<ApiException> { repository.getBlockedUsers() }
    }

    @Test
    fun reportUser_postsToTheReportsPathWithTheGivenFields() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.OK, okEmpty)

        repository.reportUser(reportedUserId = "target-1", reason = "HARASSMENT", description = "spam")

        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/moderation/reports", request.path)
        assertTrue(request.body.contains("\"reportedUserId\":\"target-1\""), request.body)
        assertTrue(request.body.contains("\"reason\":\"HARASSMENT\""), request.body)
        assertTrue(request.body.contains("\"description\":\"spam\""), request.body)
    }

    @Test
    fun reportUser_withoutAReasonPicker_defaultsToOther() = runTest {
        // There is no reason-selection UI yet (UserProfileScreen has one blanket "Report" action),
        // so the default is what every caller actually sends today.
        val repository = repositoryRespondingWith(HttpStatusCode.OK, okEmpty)

        repository.reportUser(reportedUserId = "target-1")

        assertTrue(recorded.single().body.contains("\"reason\":\"OTHER\""), recorded.single().body)
    }

    @Test
    fun everyCall_carriesTheBearerToken() = runTest {
        // Same #544 shape TwoStepRepositoryTest guards against: /api/v1/moderation/** is permitAll
        // at the filter chain, so a withheld token would not be caught there either.
        val tokenStorage = FakeTokenStorage().apply { saveTokens("access-1", "refresh-1", "u-1", "d-1") }
        val seenAuthorization = mutableListOf<String?>()
        val repository = ModerationRepository(
            ApiClient(
                tokenStorage,
                MockEngine { request ->
                    seenAuthorization += request.headers[HttpHeaders.Authorization]
                    respond(
                        content = """{"data":[],"timestamp":"2026-08-17T10:00:00Z"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            )
        )

        repository.getBlockedUsers()

        assertEquals(listOf("Bearer access-1"), seenAuthorization.map { it.orEmpty() })
    }
}

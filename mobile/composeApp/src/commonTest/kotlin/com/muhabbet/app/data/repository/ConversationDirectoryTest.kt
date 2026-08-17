package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeConversationCache
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning a conversation id back into a conversation (#543).
 *
 * Starred Messages holds ids and nothing else — a `Message` carries `conversationId` and `senderId`,
 * no name, no avatar, no participants — so before it can draw a title bar or say who spoke it has to
 * find the conversations those ids point at.
 *
 * Two failure modes are guarded here, and they pull against each other. Reading one page and giving
 * up would report "this conversation is no longer available" for anything past the top of a busy
 * user's list, which is a lie the user cannot argue with. Following the cursor forever would make an
 * id that genuinely does not exist walk the entire history, one request at a time, behind a spinner.
 * So: page until every id is accounted for, then stop; and stop regardless after
 * [ConversationDirectory.MAX_PAGES].
 */
class ConversationDirectoryTest {

    private companion object {
        const val ME = "user-me"
    }

    /** One conversation, minimal but real enough for `kotlinx.serialization` to accept it. */
    private fun conversationJson(id: String) =
        """{"id":"$id","type":"DIRECT","participants":[""" +
            """{"userId":"$ME","role":"MEMBER","isOnline":false},""" +
            """{"userId":"u2","displayName":"Ayşe","role":"MEMBER","isOnline":false}""" +
            """],"unreadCount":0,"createdAt":"2026-08-17T10:00:00Z"}"""

    private fun pageJson(ids: List<String>, nextCursor: String?) =
        """{"data":{"items":[${ids.joinToString(",") { conversationJson(it) }}],""" +
            """"nextCursor":${nextCursor?.let { "\"$it\"" } ?: "null"},""" +
            """"hasMore":${nextCursor != null}},"timestamp":"2026-08-17T10:00:00Z"}"""

    /**
     * @param pages served in order, one per request. The last page repeats if asked for more, so a
     *   test can describe a server that always claims another page exists.
     */
    private fun directory(pages: List<Pair<List<String>, String?>>, requests: MutableList<String>): ConversationDirectory {
        var served = 0
        val engine = MockEngine { request ->
            requests += request.url.encodedQuery
            val (ids, cursor) = pages[minOf(served, pages.lastIndex)]
            served++
            respond(
                content = pageJson(ids, cursor),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val tokenStorage = FakeTokenStorage().apply { saveTokens("a", "r", ME, "device-1") }
        return ConversationDirectory(
            conversationRepository = ConversationRepository(
                ApiClient(tokenStorage, engine),
                FakeConversationCache(),
            )
        )
    }

    @Test
    fun lookUp_returnsTheConversationBehindEachId() = runTest {
        val requests = mutableListOf<String>()
        val directory = directory(listOf(listOf("c1", "c2", "c3") to null), requests)

        val found = directory.lookUp(setOf("c1", "c3"))

        assertEquals(setOf("c1", "c3"), found.keys)
        assertEquals("Ayşe", found["c1"]?.participants?.first { it.userId == "u2" }?.displayName)
    }

    @Test
    fun lookUp_stopsAsSoonAsEveryIdIsAccountedFor() = runTest {
        // The common case: a handful of starred messages from conversations at the top of the list.
        // It must cost one request, not a walk of the user's history.
        val requests = mutableListOf<String>()
        val directory = directory(listOf(listOf("c1", "c2") to "cursor-2", listOf("c9") to null), requests)

        directory.lookUp(setOf("c1"))

        assertEquals(1, requests.size)
    }

    @Test
    fun lookUp_followsThePagerUntilTheConversationTurnsUp() = runTest {
        // Reading only the first page is what would make this screen claim a conversation is gone
        // when it is simply the fiftieth-most-recent.
        val requests = mutableListOf<String>()
        val directory = directory(
            listOf(listOf("c1") to "cursor-2", listOf("c2") to "cursor-3", listOf("c3") to null),
            requests,
        )

        val found = directory.lookUp(setOf("c3"))

        assertEquals(setOf("c3"), found.keys)
        assertEquals(3, requests.size)
        assertTrue(requests[1].contains("cursor=cursor-2"), "the cursor must be carried forward: ${requests[1]}")
    }

    @Test
    fun lookUp_omitsAnIdItCannotFind() = runTest {
        // Deleted, left, or past the horizon. Absent from the map, not an exception and not a
        // fabricated entry — the caller says so in its own words and refuses the tap.
        val requests = mutableListOf<String>()
        val directory = directory(listOf(listOf("c1") to null), requests)

        val found = directory.lookUp(setOf("c1", "c-gone"))

        assertEquals(setOf("c1"), found.keys)
        assertNull(found["c-gone"])
    }

    @Test
    fun lookUp_givesUpAfterTheCursorCap() = runTest {
        // A server that always claims another page, and an id that is never on one.
        val requests = mutableListOf<String>()
        val directory = directory(listOf(listOf("c1") to "cursor-next"), requests)

        val found = directory.lookUp(setOf("c-gone"))

        assertTrue(found.isEmpty())
        assertEquals(ConversationDirectory.MAX_PAGES, requests.size)
    }

    @Test
    fun lookUp_withNothingToLookUp_asksTheServerNothing() = runTest {
        val requests = mutableListOf<String>()
        val directory = directory(listOf(listOf("c1") to null), requests)

        assertTrue(directory.lookUp(emptySet()).isEmpty())
        assertTrue(requests.isEmpty())
    }
}

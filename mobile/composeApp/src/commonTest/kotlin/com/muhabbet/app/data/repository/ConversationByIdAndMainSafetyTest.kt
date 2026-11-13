package com.muhabbet.app.data.repository

import com.muhabbet.app.data.MarkingDispatcherProvider
import com.muhabbet.app.data.local.ConversationCache
import com.muhabbet.app.data.local.FakeConversationCache
import com.muhabbet.app.data.local.FakeMessageCache
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.local.MessageCache
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.DataError
import com.muhabbet.app.data.remote.DataException
import com.muhabbet.app.data.remote.HttpTransportPolicy
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.MemberRole
import com.muhabbet.shared.model.Message
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `ConversationRepository.getConversation(id)` (#800) and the repositories' main-safety (#820).
 */
class ConversationByIdAndMainSafetyTest {

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val noRetry = HttpTransportPolicy(maxRetries = 0, sleep = {})

    /**
     * A conversation the list would only reach on page four: an announcement-only group whose caller
     * is its OWNER. Before #800 the screens scanned page one, found nothing, and fell back to "not
     * announcement-only, not admin, no members".
     */
    private val groupJson = """
        {"data":{"id":"conv-60","type":"GROUP","name":"Aile","participants":[
          {"userId":"me","displayName":"Ben","role":"OWNER","isOnline":true},
          {"userId":"them","displayName":"Onlar","role":"MEMBER","isOnline":false}
        ],"unreadCount":0,"createdAt":"2026-01-01T00:00:00Z","disappearAfterSeconds":86400,
        "announcementOnly":true},"timestamp":""}
    """.trimIndent()

    private fun cachedGroup() = ConversationResponse(
        id = "conv-60",
        type = ConversationType.GROUP,
        name = "Aile (cached)",
        participants = emptyList(),
        unreadCount = 0,
        createdAt = "",
    )

    @Test
    fun aConversationNotOnPageOne_isFetchedById_withItsMembersRolesAndFlags() = runTest {
        val paths = mutableListOf<String>()
        val cache = FakeConversationCache()
        val repo = ConversationRepository(
            ApiClient(FakeTokenStorage(), MockEngine { request ->
                paths += request.url.encodedPath
                respond(groupJson, HttpStatusCode.OK, json)
            }, noRetry),
            cache,
        )

        val conversation = repo.getConversation("conv-60")

        assertEquals(listOf("/api/v1/conversations/conv-60"), paths, "one request, by id, no list paging")
        assertEquals(true, conversation.announcementOnly)
        assertEquals(86400, conversation.disappearAfterSeconds)
        assertEquals(MemberRole.OWNER, conversation.participants.single { it.userId == "me" }.role)
        assertEquals(2, conversation.participants.size)
        assertEquals("Aile", cache.getConversation("conv-60")?.name, "written through to the cache")
    }

    @Test
    fun offline_theCachedCopyAnswers() = runTest {
        val cache = FakeConversationCache().apply { upsertConversation(cachedGroup()) }
        val repo = ConversationRepository(
            ApiClient(FakeTokenStorage(), MockEngine { throw IOException("no route") }, noRetry),
            cache,
        )

        assertEquals("Aile (cached)", repo.getConversation("conv-60").name)
    }

    @Test
    fun aRefusal_isNeverMaskedByTheCache() = runTest {
        // Removed from the group since the cache was written: the server's 404 must win, or the
        // screen would keep showing a group the user is no longer in.
        val cache = FakeConversationCache().apply { upsertConversation(cachedGroup()) }
        val repo = ConversationRepository(
            ApiClient(FakeTokenStorage(), MockEngine {
                respond("""{"error":{"code":"CONV_NOT_FOUND","message":"x"},"timestamp":""}""", HttpStatusCode.NotFound, json)
            }, noRetry),
            cache,
        )

        val failure = assertFailsWith<DataException> { repo.getConversation("conv-60") }

        assertEquals(DataError.NotFound("CONV_NOT_FOUND"), failure.error)
    }

    @Test
    fun conversationRepository_touchesTheCacheOnlyOnTheIoDispatcher() = runTest {
        val dispatchers = MarkingDispatcherProvider(StandardTestDispatcher(testScheduler))
        val cache = IoAssertingConversationCache(FakeConversationCache(), dispatchers)
        val repo = ConversationRepository(
            ApiClient(FakeTokenStorage(), MockEngine { request ->
                if (request.url.encodedPath.endsWith("conv-60")) respond(groupJson, HttpStatusCode.OK, json)
                else respond("""{"data":{"items":[],"nextCursor":null,"hasMore":false},"timestamp":""}""", HttpStatusCode.OK, json)
            }, noRetry),
            cache,
            dispatchers,
        )

        repo.getConversations()
        repo.getConversation("conv-60")

        assertTrue(cache.calls > 0)
        assertEquals(0, cache.offIoCalls, "every cache call must run on the injected IO dispatcher")
    }

    @Test
    fun conversationRepository_offlineFallbackReadsTheCacheOnTheIoDispatcher() = runTest {
        val dispatchers = MarkingDispatcherProvider(StandardTestDispatcher(testScheduler))
        val cache = IoAssertingConversationCache(
            FakeConversationCache().apply { upsertConversation(cachedGroup()) },
            dispatchers,
        )
        val repo = ConversationRepository(
            ApiClient(FakeTokenStorage(), MockEngine { throw IOException("no route") }, noRetry),
            cache,
            dispatchers,
        )

        repo.getConversations()

        assertEquals(1, cache.calls)
        assertEquals(0, cache.offIoCalls)
    }

    @Test
    fun messageRepository_touchesTheCacheOnlyOnTheIoDispatcher() = runTest {
        val dispatchers = MarkingDispatcherProvider(StandardTestDispatcher(testScheduler))
        val cache = IoAssertingMessageCache(FakeMessageCache(), dispatchers)
        val repo = MessageRepository(
            ApiClient(FakeTokenStorage(), MockEngine {
                respond(
                    """{"data":{"items":[{"id":"m1","conversationId":"c1","senderId":"u","contentType":"TEXT","content":"hi","serverTimestamp":"2026-01-01T00:00:00Z","clientTimestamp":"2026-01-01T00:00:00Z"}],"nextCursor":null,"hasMore":false},"timestamp":""}""",
                    HttpStatusCode.OK,
                    json,
                )
            }, noRetry),
            cache,
            dispatchers,
        )

        repo.getMessages("c1")

        assertEquals(1, cache.calls)
        assertEquals(0, cache.offIoCalls)
    }

    private class IoAssertingConversationCache(
        private val delegate: ConversationCache,
        private val dispatchers: MarkingDispatcherProvider,
    ) : ConversationCache {
        var calls = 0
        var offIoCalls = 0

        private fun <T> record(block: () -> T): T {
            calls++
            if (!dispatchers.insideIo) offIoCalls++
            return block()
        }

        override fun getConversations() = record { delegate.getConversations() }
        override fun getConversation(id: String) = record { delegate.getConversation(id) }
        override fun upsertConversation(conv: ConversationResponse) = record { delegate.upsertConversation(conv) }
        override fun upsertConversations(conversations: List<ConversationResponse>) =
            record { delegate.upsertConversations(conversations) }
        override fun deleteConversation(id: String) = record { delegate.deleteConversation(id) }
    }

    private class IoAssertingMessageCache(
        private val delegate: MessageCache,
        private val dispatchers: MarkingDispatcherProvider,
    ) : MessageCache {
        var calls = 0
        var offIoCalls = 0

        override fun getMessagesByPage(conversationId: String, limit: Int): List<Message> {
            calls++
            if (!dispatchers.insideIo) offIoCalls++
            return delegate.getMessagesByPage(conversationId, limit)
        }

        override fun upsertMessages(messages: List<Message>) {
            calls++
            if (!dispatchers.insideIo) offIoCalls++
            delegate.upsertMessages(messages)
        }
    }
}

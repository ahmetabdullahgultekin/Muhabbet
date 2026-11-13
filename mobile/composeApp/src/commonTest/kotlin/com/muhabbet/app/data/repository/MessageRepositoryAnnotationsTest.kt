package com.muhabbet.app.data.repository

import com.muhabbet.app.data.chat.toggleReaction
import com.muhabbet.app.data.local.FakeMessageCache
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.PollTally
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType as HttpContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the message repository hands the chat about reactions, stars and polls (#775, #781, #783).
 */
class MessageRepositoryAnnotationsTest {

    private companion object {
        const val CONVERSATION_ID = "11111111-1111-4111-8111-111111111111"
        const val MESSAGE_ID = "22222222-2222-4222-8222-222222222222"
        const val REACTIONS_PATH = "/api/v1/messages/$MESSAGE_ID/reactions"
        const val THUMBS = "👍"
    }

    private class Backend(body: String) {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val repository = MessageRepository(
            apiClient = ApiClient(
                FakeTokenStorage(),
                MockEngine { request ->
                    requests += request.method to request.url.encodedPath
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, HttpContentType.Application.Json.toString()),
                    )
                },
            ),
            localCache = FakeMessageCache(),
        )
    }

    private val reactionsAnswer =
        """{"data":{"messageId":"$MESSAGE_ID","reactions":{"$THUMBS":3},"myReactions":["$THUMBS"]},""" +
            """"timestamp":"2026-11-13T10:00:00Z"}"""

    private fun message(myReactions: Set<String>) = Message(
        id = MESSAGE_ID,
        conversationId = CONVERSATION_ID,
        senderId = "someone",
        contentType = ContentType.TEXT,
        content = "",
        clientTimestamp = Instant.fromEpochMilliseconds(0),
        reactions = if (myReactions.isEmpty()) emptyMap() else mapOf(THUMBS to 1),
        myReactions = myReactions,
    )

    /** What the chat does with a reaction tap, minus the composable around it. */
    private suspend fun MessageRepository.tap(message: Message, emoji: String) {
        val toggle = message.toggleReaction(emoji)
        if (toggle.add) addReaction(message.id, emoji) else removeReaction(message.id, emoji)
    }

    @Test
    fun aQuickReactionWithAnEmojiAlreadyMine_deletesItAndNeverPostsADuplicate() = runTest {
        val backend = Backend(reactionsAnswer)

        backend.repository.tap(message(myReactions = setOf(THUMBS)), THUMBS)

        assertEquals(listOf(HttpMethod.Delete), backend.requests.map { it.first })
        assertTrue(backend.requests.none { it.first == HttpMethod.Post })
    }

    @Test
    fun aQuickReactionWithANewEmoji_postsIt() = runTest {
        val backend = Backend(reactionsAnswer)

        backend.repository.tap(message(myReactions = emptySet()), THUMBS)

        assertEquals(listOf(HttpMethod.Post to REACTIONS_PATH), backend.requests)
    }

    @Test
    fun addReaction_returnsTheServersAuthoritativeState() = runTest {
        val answer = Backend(reactionsAnswer).repository.addReaction(MESSAGE_ID, THUMBS)

        assertEquals(mapOf(THUMBS to 3), answer.reactions)
        assertEquals(setOf(THUMBS), answer.myReactions)
    }

    @Test
    fun removeReaction_encodesTheEmojiInThePath() = runTest {
        val backend = Backend(reactionsAnswer)

        backend.repository.removeReaction(MESSAGE_ID, THUMBS)

        // 👍 is U+1F44D, four UTF-8 bytes. Raw in a path it is not a valid URL.
        assertEquals("$REACTIONS_PATH/%F0%9F%91%8D", backend.requests.single().second)
    }

    @Test
    fun aHistoryPage_carriesStarsAndPollTalliesSoNoBubbleHasToFetchThem() = runTest {
        val page =
            """{"data":{"items":[{"id":"$MESSAGE_ID","conversationId":"$CONVERSATION_ID","senderId":"s",""" +
                """"contentType":"POLL","content":"{}","clientTimestamp":"2026-11-13T10:00:00Z",""" +
                """"isStarred":true,"poll":{"counts":[2,1],"totalVotes":3,"myVote":1}}],""" +
                """"nextCursor":null,"hasMore":false},"timestamp":"2026-11-13T10:00:00Z"}"""
        val backend = Backend(page)

        val loaded = backend.repository.getMessages(CONVERSATION_ID).items.single()

        assertTrue(loaded.isStarred)
        assertEquals(PollTally(counts = listOf(2, 1), totalVotes = 3, myVote = 1), loaded.poll)
        // One request for the page, none per poll.
        assertEquals(1, backend.requests.size)
    }
}

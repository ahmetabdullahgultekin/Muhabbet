package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeMessageCache
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.shared.dto.SendMessageRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The REST send path the notification inline reply depends on (#510).
 *
 * The property that matters most is the negative one. The reply used to report success
 * unconditionally, so what is worth pinning is that a rejected send **raises** rather than
 * returning something a caller could read as "fine": if `sendMessage` went back to swallowing a
 * 403, the receiver would go back to telling the user their message was delivered when it was not.
 */
class MessageRepositorySendTest {

    private companion object {
        const val CONVERSATION_ID = "11111111-1111-4111-8111-111111111111"
        const val SEND_PATH = "/api/v1/conversations/$CONVERSATION_ID/messages"
    }

    /** Records what was posted and answers with a configurable status and body. */
    private class Backend(
        private val status: HttpStatusCode,
        private val body: String,
    ) {
        val requestedMethods = mutableListOf<HttpMethod>()
        val requestedPaths = mutableListOf<String>()
        val sentRequests = mutableListOf<SendMessageRequest>()

        private val json = Json { ignoreUnknownKeys = true }

        private val engine = MockEngine { request ->
            requestedMethods += request.method
            requestedPaths += request.url.encodedPath
            if (request.method == HttpMethod.Post) {
                sentRequests += json.decodeFromString<SendMessageRequest>(
                    request.body.toByteArray().decodeToString()
                )
            }
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val cache = FakeMessageCache()
        val repository = MessageRepository(
            apiClient = ApiClient(FakeTokenStorage(), engine),
            localCache = cache,
        )
    }

    private fun sentMessageJson(content: String) =
        """{"data":{"id":"22222222-2222-4222-8222-222222222222",""" +
            """"conversationId":"$CONVERSATION_ID",""" +
            """"senderId":"33333333-3333-4333-8333-333333333333",""" +
            """"contentType":"TEXT","content":"$content","status":"SENT",""" +
            """"clientTimestamp":"2026-08-17T10:00:00Z","serverTimestamp":"2026-08-17T10:00:01Z"},""" +
            """"timestamp":"2026-08-17T10:00:01Z"}"""

    @Test
    fun sendMessage_postsToTheConversationEndpointWithAGeneratedId() = runTest {
        val backend = Backend(HttpStatusCode.OK, sentMessageJson("merhaba"))

        backend.repository.sendMessage(CONVERSATION_ID, "merhaba")

        assertEquals(listOf(HttpMethod.Post), backend.requestedMethods)
        assertEquals(listOf(SEND_PATH), backend.requestedPaths)

        val sent = backend.sentRequests.single()
        assertEquals("merhaba", sent.content)
        // A client-generated id is what makes the send idempotent on the server; without one a
        // retry would post the message twice.
        assertTrue(
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
                .matches(sent.messageId),
            "messageId should be a generated UUID, was '${sent.messageId}'"
        )
    }

    @Test
    fun sendMessage_returnsTheStoredMessageAndCachesIt() = runTest {
        val backend = Backend(HttpStatusCode.OK, sentMessageJson("merhaba"))

        val sent = backend.repository.sendMessage(CONVERSATION_ID, "merhaba")

        assertEquals("merhaba", sent.content)
        assertEquals(CONVERSATION_ID, sent.conversationId)
        // Cached so the reply is on screen when the app is next opened, not only once the first
        // history fetch comes back.
        assertEquals(listOf(sent.id), backend.cache.stored.map { it.id })
    }

    @Test
    fun sendMessage_whenTheServerRejectsIt_throwsAndCachesNothing() = runTest {
        val backend = Backend(
            HttpStatusCode.Forbidden,
            """{"error":{"code":"MSG_NOT_MEMBER","message":"Bu konusmanin uyesi degilsiniz"},"timestamp":"2026-08-17T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> {
            backend.repository.sendMessage(CONVERSATION_ID, "merhaba")
        }

        assertEquals(403, failure.status)
        assertEquals("MSG_NOT_MEMBER", failure.code)
        assertTrue(backend.cache.stored.isEmpty(), "a rejected send must not reach the cache")
    }

    @Test
    fun sendMessage_whenTheServerAnswers2xxWithNoMessage_stillThrows() = runTest {
        // A 200 carrying an empty envelope is not a send. Returning normally here is precisely how
        // a caller ends up announcing a delivery that never happened.
        val backend = Backend(HttpStatusCode.OK, """{"timestamp":"2026-08-17T10:00:00Z"}""")

        assertFailsWith<Exception> { backend.repository.sendMessage(CONVERSATION_ID, "merhaba") }
        assertTrue(backend.cache.stored.isEmpty())
    }
}

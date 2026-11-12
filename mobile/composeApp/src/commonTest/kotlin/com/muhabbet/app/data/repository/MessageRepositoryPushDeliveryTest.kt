package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeMessageCache
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The push path's DELIVERED, in the persist-then-ack order the socket path now uses (#753).
 */
class MessageRepositoryPushDeliveryTest {

    private val jsonContentType = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private class Backend(
        val historyStatus: HttpStatusCode = HttpStatusCode.OK,
        val historyIds: List<String> = listOf(MESSAGE),
        val cache: FakeMessageCache = FakeMessageCache(),
    ) {
        val events = mutableListOf<String>()
    }

    private fun repository(backend: Backend): MessageRepository {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Get && path == "/api/v1/conversations/$CONVERSATION/messages" -> {
                    // What the cache holds at the moment the history answers; the ack must come after.
                    backend.events += "history"
                    respond(page(backend.historyIds), backend.historyStatus, jsonContentType)
                }
                request.method == HttpMethod.Post && path == "/api/v1/messages/$MESSAGE/delivered" -> {
                    backend.events += "delivered(cached=${backend.cache.stored.map { it.id }})"
                    respond("", HttpStatusCode.NoContent)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        return MessageRepository(ApiClient(FakeTokenStorage(), engine), backend.cache)
    }

    @Test
    fun `should store the message before acking it when a push announces it`() = runTest {
        val backend = Backend()

        val acked = repository(backend).storeThenMarkDelivered(MESSAGE, CONVERSATION)

        assertTrue(acked)
        assertEquals(listOf("history", "delivered(cached=[$MESSAGE])"), backend.events)
    }

    @Test
    fun `should not ack when the message cannot be fetched`() = runTest {
        val backend = Backend(historyStatus = HttpStatusCode.InternalServerError)

        val acked = repository(backend).storeThenMarkDelivered(MESSAGE, CONVERSATION)

        assertFalse(acked)
        assertEquals(listOf("history"), backend.events)
    }

    @Test
    fun `should not ack when the cache write fails`() = runTest {
        val backend = Backend(cache = FakeMessageCache(failOnUpsert = true))

        val acked = repository(backend).storeThenMarkDelivered(MESSAGE, CONVERSATION)

        assertFalse(acked)
        assertEquals(listOf("history"), backend.events)
    }

    @Test
    fun `should not ack when the latest page does not contain the message`() = runTest {
        val backend = Backend(historyIds = listOf("some-other-message"))

        val acked = repository(backend).storeThenMarkDelivered(MESSAGE, CONVERSATION)

        assertFalse(acked)
        assertEquals(listOf("history"), backend.events)
    }

    private fun page(ids: List<String>): String {
        val items = ids.joinToString(",") { id ->
            """{"id":"$id","conversationId":"$CONVERSATION","senderId":"user-b","contentType":"TEXT",""" +
                """"content":"hi","status":"DELIVERED","clientTimestamp":"2026-11-12T10:00:00Z"}"""
        }
        return """{"data":{"items":[$items],"nextCursor":null,"hasMore":false},"timestamp":"2026-11-12T10:00:00Z"}"""
    }

    private companion object {
        const val MESSAGE = "msg-1"
        const val CONVERSATION = "conv-1"
    }
}

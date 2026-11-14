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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #780 at the client: the KVKK export and erasure calls hit the routes the server actually maps,
 * and the export is walked to the end instead of being decoded into `Unit` and thrown away.
 *
 * Paths are asserted as **literals**, not read back from `ApiRoutes`: a test that reuses the
 * constant would agree with a wrong constant. These literals must match `UserDataRoutingTest` on
 * the backend, which pins the same two URLs through the real `DispatcherServlet`.
 */
class DataExportRepositoryTest {

    private class Recorded(val method: String, val path: String, val query: Map<String, String>)

    private val recorded = mutableListOf<Recorded>()

    private fun apiClient(respondWith: (Recorded) -> Pair<HttpStatusCode, String>): Pair<ApiClient, FakeTokenStorage> {
        val tokens = FakeTokenStorage().apply { saveTokens("access-1", "refresh-1", "user-1", "device-1") }
        val engine = MockEngine { request ->
            val r = Recorded(
                method = request.method.value,
                path = request.url.encodedPath,
                query = request.url.parameters.entries().associate { it.key to it.value.single() },
            )
            recorded += r
            val (status, body) = respondWith(r)
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return ApiClient(tokens, engine) to tokens
    }

    private fun item(id: String) = """{"id":"$id"}"""

    private fun page(
        messages: List<String>, messagesMore: Boolean, messagesNext: String?,
        media: List<String>, mediaMore: Boolean, mediaNext: String?,
    ): String {
        fun cursor(c: String?) = if (c == null) "null" else "\"$c\""
        return """{"data":{
            "exportedAt":"2026-11-13T10:00:00Z",
            "profile":{"displayName":"Test"},
            "messages":{"items":[${messages.joinToString(",") { item(it) }}],"hasMore":$messagesMore,"nextCursor":${cursor(messagesNext)},"totalCount":5},
            "mediaFiles":{"items":[${media.joinToString(",") { item(it) }}],"hasMore":$mediaMore,"nextCursor":${cursor(mediaNext)},"totalCount":2}
        },"timestamp":"2026-11-13T10:00:00Z"}"""
    }

    @Test
    fun exportAll_walksEveryPageAtTheServedPath_andMergesWithoutDuplicates() = runTest {
        // Messages need three pages; media is done after the second. On page 3 the server still
        // returns media's final page (its cursor did not move) — those items must not be added twice.
        val pages = listOf(
            page(listOf("m1", "m2"), true, "c-m1", listOf("f1"), true, "c-f1"),
            page(listOf("m3", "m4"), true, "c-m2", listOf("f2"), false, null),
            page(listOf("m5"), false, null, listOf("f2"), false, null),
        )
        val (client, _) = apiClient { r -> HttpStatusCode.OK to pages[recorded.size - 1] }
        val progress = mutableListOf<Int>()

        val document = DataExportRepository(client).exportAll { progress += it }

        assertEquals(List(3) { "GET" to "/api/v1/users/me/data-export" }, recorded.map { it.method to it.path })
        // The cursors go back under the parameter names the controller reads.
        assertEquals(mapOf("pageSize" to "500"), recorded[0].query)
        assertEquals(mapOf("pageSize" to "500", "messagesCursor" to "c-m1", "mediaCursor" to "c-f1"), recorded[1].query)
        assertEquals("c-m2", recorded[2].query["messagesCursor"])
        assertEquals(listOf(1, 2, 3), progress)

        val messages = document["messages"]!!.jsonObject
        assertEquals(listOf("m1", "m2", "m3", "m4", "m5"), messages.ids())
        assertFalse(messages["hasMore"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(5, messages["totalCount"]!!.jsonPrimitive.int)
        assertEquals(listOf("f1", "f2"), document["mediaFiles"]!!.jsonObject.ids())
        // Every non-paginated category comes through untouched.
        assertEquals("Test", document["profile"]!!.jsonObject["displayName"]!!.jsonPrimitive.content)
    }

    @Test
    fun exportAll_stops_whenTheServerRepeatsTheSameCursor() = runTest {
        // A server bug must cost one extra page, not an infinite loop — and the repeated page's
        // items must not be counted twice.
        val stuck = page(listOf("m1"), true, "same", emptyList(), false, null)
        val (client, _) = apiClient { HttpStatusCode.OK to stuck }

        val document = DataExportRepository(client).exportAll()

        assertEquals(2, recorded.size)
        assertEquals(listOf("m1"), document["messages"]!!.jsonObject.ids())
    }

    @Test
    fun exportAll_fails_ratherThanReturningAPartialExport() = runTest {
        val first = page(listOf("m1"), true, "c1", emptyList(), false, null)
        val (client, _) = apiClient {
            if (recorded.size == 1) HttpStatusCode.OK to first
            else HttpStatusCode.InternalServerError to """{"error":{"code":"INTERNAL_ERROR","message":"x"},"timestamp":"t"}"""
        }

        assertFailsWith<ApiException> { DataExportRepository(client).exportAll() }
    }

    @Test
    fun encodeToBytes_producesJsonThatReadsBackToTheSameDocument() = runTest {
        val (client, _) = apiClient { HttpStatusCode.OK to page(listOf("m1"), false, null, emptyList(), false, null) }
        val repository = DataExportRepository(client)
        val document = repository.exportAll()

        val decoded = Json.parseToJsonElement(repository.encodeToBytes(document).decodeToString())

        assertEquals(document, decoded)
    }

    @Test
    fun encodeToBytes_isByteForByteWhatThePrettyPrinterWouldHaveWritten() = runTest {
        // The streaming writer replaces `Json { prettyPrint = true }.encodeToString(...)`; a user
        // opening the file must not be able to tell. Escapes, unicode, nesting, empties and every
        // scalar kind are in here, because those are where a hand-written writer would drift.
        val document = Json.parseToJsonElement(
            """{"s":"a\"b\\c\n\t\u0001 ğüşİ 😀","n":-1.5e3,"i":42,"t":true,"f":false,"z":null,
               "e":{},"a":[],"nested":{"list":[{"k":[1,[2,{}]]},"x"]}}"""
        ) as JsonObject
        val expected = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), document)

        val bytes = DataExportRepository(apiClient { HttpStatusCode.OK to "{}" }.first).encodeToBytes(document)

        assertEquals(expected, bytes.decodeToString())
        assertContentEquals(expected.encodeToByteArray(), bytes)
    }

    @Test
    fun theRepository_defaultsToTheDefaultDispatcher_notTheCallers() {
        // The screen calls from Main. If this default ever becomes "whatever the caller is on",
        // a large export blocks the UI thread again.
        assertSame(Dispatchers.Default, DataExportRepository(apiClient { HttpStatusCode.OK to "{}" }.first).workDispatcher)
    }

    @Test
    fun exportAllAndEncode_bothRunOnTheWorkDispatcher() = runTest {
        val (client, _) = apiClient { HttpStatusCode.OK to page(listOf("m1"), false, null, emptyList(), false, null) }
        val work = RecordingDispatcher(StandardTestDispatcher(testScheduler))
        val repository = DataExportRepository(client, workDispatcher = work)

        val document = repository.exportAll()
        val afterExport = work.dispatches
        repository.encodeToBytes(document)

        assertTrue(afterExport > 0, "exportAll never moved to the work dispatcher")
        assertTrue(work.dispatches > afterExport, "encodeToBytes never moved to the work dispatcher")
    }

    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatches = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    @Test
    fun deleteAccount_deletesTheAccountItself_andOnlyThenClearsTokens() = runTest {
        val (client, tokens) = apiClient { HttpStatusCode.OK to """{"data":null,"timestamp":"t"}""" }

        AuthRepository(client, tokens).deleteAccount()

        assertEquals(listOf("DELETE" to "/api/v1/users/me"), recorded.map { it.method to it.path })
        assertFalse(tokens.isLoggedIn())
    }

    @Test
    fun deleteAccount_keepsTheSession_whenTheServerRefuses() = runTest {
        val (client, tokens) = apiClient {
            HttpStatusCode.InternalServerError to """{"error":{"code":"INTERNAL_ERROR","message":"x"},"timestamp":"t"}"""
        }

        assertFailsWith<ApiException> { AuthRepository(client, tokens).deleteAccount() }
        assertTrue(tokens.isLoggedIn())
    }

    private fun JsonObject.ids(): List<String> =
        this["items"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
}

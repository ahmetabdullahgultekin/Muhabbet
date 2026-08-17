package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeMessageCache
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
import kotlin.test.assertNotNull

/**
 * What actually leaves the phone when someone searches (#622).
 *
 * The bug was string interpolation into a query string — `?q=$query` — so this asserts on the URL
 * the engine was handed, not on the result. A test that only checked "search returns a list" passed
 * happily while the request was mangled, because [ApiClient] does not inspect the HTTP status
 * (#374): a 400 decodes to `data = null` and reaches the screen as "no results".
 *
 * The assertions are on the **decoded parameter**, deliberately. Asserting the literal percent
 * escapes would pin the encoder's choices — `%20` versus `+`, upper versus lower case hex — none of
 * which the server cares about, and all of which would make this test fail on a Ktor upgrade that
 * changed nothing that matters.
 */
class MessageSearchQueryTest {

    /** The path the mock engine was asked for, and the parsed value of `q`. */
    private class Captured {
        var url: String? = null
    }

    private fun repositoryCapturing(captured: Captured): MessageRepository {
        val engine = MockEngine { request ->
            captured.url = request.url.toString()
            respond(
                content = """{"data":{"items":[],"nextCursor":null,"hasMore":false},"timestamp":"2026-08-17T00:00:00Z"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val tokenStorage = FakeTokenStorage()
        tokenStorage.saveTokens("access", "refresh", "user1", "device1")
        return MessageRepository(ApiClient(tokenStorage, engine), FakeMessageCache())
    }

    /** `q` as the server would parse it, after undoing whatever encoding the client chose. */
    private fun decodedQ(url: String): String {
        val query = url.substringAfter('?', "")
        val raw = query.split('&')
            .firstOrNull { it.startsWith("q=") }
            ?.removePrefix("q=")
            ?: return ""
        // Ktor may emit either `+` or `%20` for a space; both mean space in a query string.
        val withSpaces = raw.replace("+", " ")

        // Bytes reassembled before decoding, not one escape at a time: a Turkish "ş" is two
        // percent-escapes and decoding each alone yields two replacement characters.
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < withSpaces.length) {
            if (withSpaces[i] == '%' && i + 2 < withSpaces.length) {
                bytes.add(withSpaces.substring(i + 1, i + 3).toInt(16).toByte())
                i += 3
            } else {
                withSpaces[i].toString().encodeToByteArray().forEach(bytes::add)
                i++
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    @Test
    fun a_query_with_a_space_survives_the_url() = runTest {
        val captured = Captured()
        repositoryCapturing(captured).searchMessages("merhaba dünya")

        val url = assertNotNull(captured.url, "no request was made")
        assertEquals("merhaba dünya", decodedQ(url))
    }

    /**
     * The one that made the feature look broken rather than wrong: unencoded, `&` ends the
     * parameter, so the server was asked for `q=a ` and handed ` b` as a stray parameter.
     */
    @Test
    fun an_ampersand_does_not_split_the_query_into_two_parameters() = runTest {
        val captured = Captured()
        repositoryCapturing(captured).searchMessages("kahve & çay")

        val url = assertNotNull(captured.url, "no request was made")
        assertEquals("kahve & çay", decodedQ(url))
        assertEquals(
            2,
            url.substringAfter('?').split('&').size,
            "expected exactly q and limit; the user's & leaked into the query string: $url"
        )
    }

    /**
     * Worse than a visible failure: unencoded, everything after `#` is a fragment and never leaves
     * the device. The user sees "no results" for a search the server was never asked.
     */
    @Test
    fun a_hash_reaches_the_server_instead_of_becoming_a_fragment() = runTest {
        val captured = Captured()
        repositoryCapturing(captured).searchMessages("#tatil")

        val url = assertNotNull(captured.url, "no request was made")
        assertEquals("#tatil", decodedQ(url))
    }

    /** Turkish is the default locale; dotless ı and ş are ordinary input, not an edge case. */
    @Test
    fun turkish_characters_survive_the_url() = runTest {
        val captured = Captured()
        repositoryCapturing(captured).searchMessages("nasılsın şeker")

        val url = assertNotNull(captured.url, "no request was made")
        assertEquals("nasılsın şeker", decodedQ(url))
    }

    @Test
    fun a_plus_is_not_silently_turned_into_a_space() = runTest {
        val captured = Captured()
        repositoryCapturing(captured).searchMessages("c++")

        val url = assertNotNull(captured.url, "no request was made")
        assertEquals("c++", decodedQ(url))
    }
}

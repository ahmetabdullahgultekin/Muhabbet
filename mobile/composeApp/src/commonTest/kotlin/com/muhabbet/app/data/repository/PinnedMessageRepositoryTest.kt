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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * #790: the client half of pin-in-chat. As with every repository test here, the first thing worth
 * asserting is the method and path — a test that only checks how a mocked 200 decodes would pass
 * against a URL nothing serves, which is how a backend vertical shipped with no client at all.
 */
class PinnedMessageRepositoryTest {

    private companion object {
        const val CONVERSATION_ID = "11111111-1111-4111-8111-111111111111"
        const val MESSAGE_ID = "22222222-2222-4222-8222-222222222222"
        const val PINS_PATH = "/api/v1/conversations/$CONVERSATION_ID/pinned-messages"
    }

    private class Recorded(val method: String, val path: String, val body: String)

    private val recorded = mutableListOf<Recorded>()

    private fun repository(status: HttpStatusCode, body: String) = PinnedMessageRepository(
        ApiClient(
            FakeTokenStorage().apply { saveTokens("access-1", "refresh-1", "user-1", "device-1") },
            MockEngine { request ->
                recorded += Recorded(
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

    private val pinJson =
        """{"conversationId":"$CONVERSATION_ID","messageId":"$MESSAGE_ID",""" +
            """"pinnedBy":"33333333-3333-4333-8333-333333333333","pinnedAt":"2026-11-13T10:00:00Z"}"""

    @Test
    fun getPinned_readsTheConversationsPins() = runTest {
        val repo = repository(HttpStatusCode.OK, """{"data":[$pinJson],"timestamp":"2026-11-13T10:00:00Z"}""")

        val pins = repo.getPinned(CONVERSATION_ID)

        assertEquals(listOf("GET" to PINS_PATH), recorded.map { it.method to it.path })
        assertEquals(listOf(MESSAGE_ID), pins.map { it.messageId })
    }

    @Test
    fun pin_postsTheMessageIdAndReturnsThePin() = runTest {
        val repo = repository(HttpStatusCode.Created, """{"data":$pinJson,"timestamp":"2026-11-13T10:00:00Z"}""")

        val result = repo.pin(CONVERSATION_ID, MESSAGE_ID)

        assertEquals(listOf("POST" to PINS_PATH), recorded.map { it.method to it.path })
        assertTrue(recorded.single().body.contains("\"messageId\":\"$MESSAGE_ID\""), recorded.single().body)
        assertEquals(MESSAGE_ID, assertIs<PinResult.Pinned>(result).pin.messageId)
    }

    @Test
    fun pin_whenTheConversationAlreadyHasThreePins_reportsTheLimitRatherThanThrowing() = runTest {
        val repo = repository(
            HttpStatusCode.Conflict,
            """{"error":{"code":"MSG_PIN_LIMIT_REACHED","message":"Sabitlenmiş mesaj sınırına ulaşıldı"},""" +
                """"timestamp":"2026-11-13T10:00:00Z"}""",
        )

        assertEquals(PinResult.LimitReached, repo.pin(CONVERSATION_ID, MESSAGE_ID))
    }

    @Test
    fun pin_whenTheCallerIsNotAMember_stillFails() = runTest {
        val repo = repository(
            HttpStatusCode.Forbidden,
            """{"error":{"code":"MSG_NOT_MEMBER","message":"x"},"timestamp":"2026-11-13T10:00:00Z"}""",
        )

        val failure = assertFailsWith<ApiException> { repo.pin(CONVERSATION_ID, MESSAGE_ID) }

        assertEquals("MSG_NOT_MEMBER", failure.code)
    }

    @Test
    fun unpin_deletesThePinByMessageId() = runTest {
        val repo = repository(HttpStatusCode.OK, """{"data":null,"timestamp":"2026-11-13T10:00:00Z"}""")

        repo.unpin(CONVERSATION_ID, MESSAGE_ID)

        assertEquals(listOf("DELETE" to "$PINS_PATH/$MESSAGE_ID"), recorded.map { it.method to it.path })
    }
}

package com.muhabbet.shared.protocol

import com.muhabbet.shared.dto.MessageReactionsResponse
import com.muhabbet.shared.dto.PinnedMessageInfo
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.PollTally
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wire contract for the per-message annotations a chat shows beside the text: reactions,
 * poll tallies, stars and pins (#775, #781, #783, #790).
 *
 * Kept apart from [WsMessageSerializationTest] because two of these pin *compatibility* rather than
 * shape: a payload from before a field existed must still decode, and decode to "nothing known"
 * rather than to a wrong answer.
 */
class ChatAnnotationFramesSerializationTest {

    private val restJson = Json { ignoreUnknownKeys = true }

    @Test
    fun should_serialize_PollUpdated_with_the_poll_updated_discriminator() {
        val frame = WsMessage.PollUpdated(
            messageId = "m1",
            conversationId = "c1",
            voterId = "u2",
            optionIndex = 1,
            counts = listOf(0, 2, 1),
            totalVotes = 3
        )

        val json = wsJson.encodeToString(WsMessage.serializer(), frame)

        assertTrue(json.contains("\"type\":\"poll.updated\""), json)
        assertEquals(frame, wsJson.decodeFromString<WsMessage>(json))
    }

    @Test
    fun should_serialize_PinnedMessagesChanged_with_the_message_pinned_discriminator() {
        val frame = WsMessage.PinnedMessagesChanged(
            conversationId = "c1",
            changedBy = "u1",
            pins = listOf(PinnedMessageInfo("c1", "m1", "u1", "2026-11-13T10:00:00Z"))
        )

        val json = wsJson.encodeToString(WsMessage.serializer(), frame)

        assertTrue(json.contains("\"type\":\"message.pinned\""), json)
        assertEquals(frame, wsJson.decodeFromString<WsMessage>(json))
    }

    @Test
    fun should_carry_the_authoritative_count_on_a_reaction_frame() {
        val json = """
            {"type":"message.reaction","messageId":"m1","conversationId":"c1",
             "userId":"u1","emoji":"👍","action":"add","count":2}
        """.trimIndent()

        val frame = wsJson.decodeFromString<WsMessage>(json)

        assertIs<WsMessage.MessageReaction>(frame)
        assertEquals(2, frame.count)
    }

    @Test
    fun should_decode_a_reaction_frame_from_an_older_server_with_no_count() {
        val json = """
            {"type":"message.reaction","messageId":"m1","conversationId":"c1",
             "userId":"u1","emoji":"👍","action":"add"}
        """.trimIndent()

        val frame = wsJson.decodeFromString<WsMessage>(json)

        assertIs<WsMessage.MessageReaction>(frame)
        assertNull(frame.count)
    }

    @Test
    fun should_decode_a_message_without_annotations_as_not_starred_and_no_poll() {
        // A cached payload, or one from a server before #775/#781, must not read as "starred".
        val json = """
            {"id":"m1","conversationId":"c1","senderId":"u1","contentType":"TEXT",
             "content":"hi","clientTimestamp":"2026-11-13T10:00:00Z"}
        """.trimIndent()

        val message = restJson.decodeFromString(Message.serializer(), json)

        assertFalse(message.isStarred)
        assertNull(message.poll)
    }

    @Test
    fun should_roundtrip_a_message_carrying_star_and_poll_tally() {
        val json = """
            {"id":"m1","conversationId":"c1","senderId":"u1","contentType":"POLL",
             "content":"{}","clientTimestamp":"2026-11-13T10:00:00Z","isStarred":true,
             "poll":{"counts":[1,0],"totalVotes":1,"myVote":0}}
        """.trimIndent()

        val message = restJson.decodeFromString(Message.serializer(), json)

        assertTrue(message.isStarred)
        assertEquals(PollTally(counts = listOf(1, 0), totalVotes = 1, myVote = 0), message.poll)
    }

    @Test
    fun should_decode_MessageReactionsResponse() {
        val json = """{"messageId":"m1","reactions":{"👍":2},"myReactions":["👍"]}"""

        val response = restJson.decodeFromString(MessageReactionsResponse.serializer(), json)

        assertEquals(mapOf("👍" to 2), response.reactions)
        assertEquals(setOf("👍"), response.myReactions)
    }
}

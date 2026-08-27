package com.muhabbet.shared.protocol

import com.muhabbet.shared.model.ContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wire contract for disappearing messages (#513).
 *
 * Two halves, and the protocol has to carry both. `message.new` gains the deadline so a recipient
 * can remove the message on time by itself; `message.expired` is the server saying the time is up,
 * which is what covers a client whose clock disagrees or whose app was asleep when the moment came.
 *
 * Every discriminator here is a string three independent implementations agree on — backend, app
 * and the Python test bot — so the literals are asserted rather than round-tripped through the
 * Kotlin type, which would pass even if the name changed.
 */
class MessageExpiryProtocolTest {

    @Test
    fun should_serialize_MessageExpired_with_correct_type() {
        val json = wsJson.encodeToString(
            WsMessage.serializer(),
            WsMessage.MessageExpired(
                messageId = "msg-1",
                conversationId = "conv-1",
                expiredAt = 1_700_000_000_000
            )
        )

        assertTrue(json.contains(""""type":"message.expired""""), json)
        assertTrue(json.contains(""""messageId":"msg-1""""), json)
        assertTrue(json.contains(""""conversationId":"conv-1""""), json)
    }

    @Test
    fun should_deserialize_MessageExpired_from_json() {
        val decoded = wsJson.decodeFromString(
            WsMessage.serializer(),
            """{"type":"message.expired","messageId":"m1","conversationId":"c1","expiredAt":1700000000000}"""
        )

        val expired = assertIs<WsMessage.MessageExpired>(decoded)
        assertEquals("m1", expired.messageId)
        assertEquals("c1", expired.conversationId)
        assertEquals(1_700_000_000_000, expired.expiredAt)
    }

    /**
     * Distinct from `message.deleted` on purpose. A deletion is somebody's act and renders as a
     * tombstone; an expiry is nobody's and leaves nothing behind, because the server drops the row
     * from every read path. Sharing one frame would mean the client could not tell the two apart.
     */
    @Test
    fun should_use_a_different_discriminator_from_a_deletion() {
        val expired = wsJson.encodeToString(
            WsMessage.serializer(),
            WsMessage.MessageExpired("m1", "c1", 1L)
        )
        val deleted = wsJson.encodeToString(
            WsMessage.serializer(),
            WsMessage.MessageDeleted("m1", "c1", "u1", 1L)
        )

        assertTrue(expired.contains(""""type":"message.expired""""), expired)
        assertTrue(deleted.contains(""""type":"message.deleted""""), deleted)
    }

    @Test
    fun should_carry_the_expiry_deadline_on_a_new_message() {
        val decoded = wsJson.decodeFromString(
            WsMessage.serializer(),
            """
            {"type":"message.new","messageId":"m1","conversationId":"c1","senderId":"s1",
             "senderName":"Ayşe","content":"gizli","contentType":"TEXT",
             "serverTimestamp":1700000000000,"expiresAt":1700000030000}
            """.trimIndent()
        )

        val message = assertIs<WsMessage.NewMessage>(decoded)
        assertEquals(1_700_000_030_000, message.expiresAt)
    }

    /**
     * A message in a chat with no timer set carries no deadline, and a frame from a server that
     * predates this field must still decode. Both land on null, which the client reads as "never
     * disappears" — the safe answer, since the alternative would be removing messages nobody asked
     * to expire.
     */
    @Test
    fun should_default_the_expiry_to_null_when_the_frame_omits_it() {
        val decoded = wsJson.decodeFromString(
            WsMessage.serializer(),
            """
            {"type":"message.new","messageId":"m1","conversationId":"c1","senderId":"s1",
             "senderName":null,"content":"merhaba","contentType":"TEXT",
             "serverTimestamp":1700000000000}
            """.trimIndent()
        )

        val message = assertIs<WsMessage.NewMessage>(decoded)
        assertNull(message.expiresAt)
        assertEquals(ContentType.TEXT, message.contentType)
    }
}

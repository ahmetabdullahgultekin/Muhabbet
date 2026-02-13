package com.muhabbet.shared.protocol

import com.muhabbet.shared.model.CallEndReason
import com.muhabbet.shared.model.CallType
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.model.PresenceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests that WsMessage serialization/deserialization works correctly.
 * Critical because backend + mobile + bots all depend on exact same JSON format.
 * SerialName values ("type" discriminator) must match exactly.
 */
class WsMessageSerializationTest {

    // ─── Client → Server messages ─────────────────

    @Test
    fun should_serialize_SendMessage_with_correct_type() {
        val msg = WsMessage.SendMessage(
            requestId = "req-1",
            messageId = "msg-1",
            conversationId = "conv-1",
            content = "Hello world",
            contentType = ContentType.TEXT
        )
        val json = wsJson.encodeToString(WsMessage.serializer(), msg)
        assertTrue(json.contains(""""type":"message.send""""))
        assertTrue(json.contains(""""requestId":"req-1""""))
        assertTrue(json.contains(""""content":"Hello world""""))
    }

    @Test
    fun should_deserialize_SendMessage_from_json() {
        val json = """
            {"type":"message.send","requestId":"r1","messageId":"m1",
             "conversationId":"c1","content":"test","contentType":"TEXT"}
        """.trimIndent()
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.SendMessage>(msg)
        assertEquals("r1", msg.requestId)
        assertEquals("test", msg.content)
        assertEquals(ContentType.TEXT, msg.contentType)
    }

    @Test
    fun should_serialize_AckMessage_with_correct_type() {
        val msg = WsMessage.AckMessage(
            messageId = "msg-1",
            conversationId = "conv-1",
            status = MessageStatus.DELIVERED
        )
        val json = wsJson.encodeToString(WsMessage.serializer(), msg)
        assertTrue(json.contains(""""type":"message.ack""""))
        assertTrue(json.contains(""""status":"DELIVERED""""))
    }

    @Test
    fun should_deserialize_AckMessage_from_json() {
        val json = """{"type":"message.ack","messageId":"m1","conversationId":"c1","status":"READ"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.AckMessage>(msg)
        assertEquals(MessageStatus.READ, msg.status)
    }

    @Test
    fun should_serialize_TypingIndicator() {
        val msg = WsMessage.TypingIndicator(conversationId = "conv-1", isTyping = true)
        val json = wsJson.encodeToString(WsMessage.serializer(), msg)
        assertTrue(json.contains(""""type":"presence.typing""""))
        assertTrue(json.contains(""""isTyping":true"""))
    }

    @Test
    fun should_serialize_GoOnline() {
        val msg = WsMessage.GoOnline
        val json = wsJson.encodeToString(WsMessage.serializer(), msg)
        assertTrue(json.contains(""""type":"presence.online""""))
    }

    @Test
    fun should_serialize_Ping() {
        val json = wsJson.encodeToString(WsMessage.serializer(), WsMessage.Ping)
        assertTrue(json.contains(""""type":"ping""""))
    }

    // ─── Call Signaling ───────────────────────────

    @Test
    fun should_serialize_CallInitiate_with_sdp() {
        val msg = WsMessage.CallInitiate(
            callId = "call-1",
            targetUserId = "user-2",
            callType = CallType.VOICE,
            sdpOffer = "v=0\r\no=..."
        )
        val json = wsJson.encodeToString(WsMessage.serializer(), msg)
        assertTrue(json.contains(""""type":"call.initiate""""))
        assertTrue(json.contains(""""callType":"VOICE""""))
        assertTrue(json.contains(""""sdpOffer":"v=0"""))
    }

    @Test
    fun should_deserialize_CallAnswer() {
        val json = """{"type":"call.answer","callId":"c1","accepted":true,"sdpAnswer":"v=0"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.CallAnswer>(msg)
        assertEquals(true, msg.accepted)
        assertEquals("v=0", msg.sdpAnswer)
    }

    @Test
    fun should_serialize_CallEnd_with_reason() {
        val msg = WsMessage.CallEnd(callId = "call-1", reason = CallEndReason.DECLINED)
        val json = wsJson.encodeToString(WsMessage.serializer(), msg)
        assertTrue(json.contains(""""type":"call.end""""))
        assertTrue(json.contains(""""reason":"DECLINED""""))
    }

    @Test
    fun should_deserialize_CallIncoming() {
        val json = """{"type":"call.incoming","callId":"c1","callerId":"u1","callerName":"Ali","callType":"VIDEO"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.CallIncoming>(msg)
        assertEquals("Ali", msg.callerName)
        assertEquals(CallType.VIDEO, msg.callType)
    }

    // ─── Server → Client messages ─────────────────

    @Test
    fun should_deserialize_NewMessage() {
        val json = """
            {"type":"message.new","messageId":"m1","conversationId":"c1",
             "senderId":"u1","senderName":"Ali","content":"Merhaba",
             "contentType":"TEXT","serverTimestamp":1707840000000}
        """.trimIndent()
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.NewMessage>(msg)
        assertEquals("Merhaba", msg.content)
        assertEquals(1707840000000L, msg.serverTimestamp)
    }

    @Test
    fun should_deserialize_StatusUpdate() {
        val json = """
            {"type":"message.status","messageId":"m1","conversationId":"c1",
             "userId":"u2","status":"READ","timestamp":1707840000000}
        """.trimIndent()
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.StatusUpdate>(msg)
        assertEquals(MessageStatus.READ, msg.status)
    }

    @Test
    fun should_deserialize_ServerAck_ok() {
        val json = """{"type":"ack","requestId":"r1","messageId":"m1","status":"OK","serverTimestamp":1707840000000}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.ServerAck>(msg)
        assertEquals(AckStatus.OK, msg.status)
        assertEquals(1707840000000L, msg.serverTimestamp)
    }

    @Test
    fun should_deserialize_ServerAck_error() {
        val json = """{"type":"ack","requestId":"r1","messageId":"","status":"ERROR","errorCode":"MSG_CONVERSATION_NOT_FOUND","errorMessage":"Not found"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.ServerAck>(msg)
        assertEquals(AckStatus.ERROR, msg.status)
        assertEquals("MSG_CONVERSATION_NOT_FOUND", msg.errorCode)
    }

    @Test
    fun should_deserialize_PresenceUpdate() {
        val json = """{"type":"presence.update","userId":"u1","status":"ONLINE"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.PresenceUpdate>(msg)
        assertEquals(PresenceStatus.ONLINE, msg.status)
    }

    // ─── Group Events ─────────────────────────────

    @Test
    fun should_deserialize_GroupMemberAdded() {
        val json = """
            {"type":"group.member_added","conversationId":"c1","addedBy":"u1",
             "members":[{"userId":"u2","displayName":"Ayse","role":"MEMBER"}]}
        """.trimIndent()
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.GroupMemberAdded>(msg)
        assertEquals(1, msg.members.size)
        assertEquals("Ayse", msg.members[0].displayName)
    }

    @Test
    fun should_deserialize_GroupMemberRemoved() {
        val json = """{"type":"group.member_removed","conversationId":"c1","removedBy":"u1","userId":"u2"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.GroupMemberRemoved>(msg)
        assertEquals("u2", msg.userId)
    }

    @Test
    fun should_deserialize_GroupInfoUpdated() {
        val json = """{"type":"group.info_updated","conversationId":"c1","updatedBy":"u1","name":"New Name"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.GroupInfoUpdated>(msg)
        assertEquals("New Name", msg.name)
    }

    // ─── Message Management ───────────────────────

    @Test
    fun should_deserialize_MessageDeleted() {
        val json = """{"type":"message.deleted","messageId":"m1","conversationId":"c1","deletedBy":"u1","timestamp":1707840000000}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.MessageDeleted>(msg)
    }

    @Test
    fun should_deserialize_MessageEdited() {
        val json = """{"type":"message.edited","messageId":"m1","conversationId":"c1","editedBy":"u1","newContent":"Updated","editedAt":1707840000000}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.MessageEdited>(msg)
        assertEquals("Updated", msg.newContent)
    }

    @Test
    fun should_deserialize_MessageReaction() {
        val json = """{"type":"message.reaction","messageId":"m1","conversationId":"c1","userId":"u1","emoji":"👍","action":"add"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.MessageReaction>(msg)
        assertEquals("add", msg.action)
    }

    @Test
    fun should_deserialize_Pong() {
        val json = """{"type":"pong"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.Pong>(msg)
    }

    @Test
    fun should_deserialize_Error() {
        val json = """{"type":"error","code":"AUTH_EXPIRED","message":"Token expired"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.Error>(msg)
        assertEquals("AUTH_EXPIRED", msg.code)
    }

    // ─── Round-trip tests ─────────────────────────

    @Test
    fun should_roundtrip_SendMessage_with_forwarding() {
        val original = WsMessage.SendMessage(
            requestId = "req-99",
            messageId = "msg-99",
            conversationId = "conv-1",
            content = "Forwarded text",
            contentType = ContentType.TEXT,
            forwardedFrom = "msg-original"
        )
        val json = wsJson.encodeToString(WsMessage.serializer(), original)
        val decoded = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.SendMessage>(decoded)
        assertEquals("msg-original", decoded.forwardedFrom)
    }

    @Test
    fun should_roundtrip_NewMessage_with_media() {
        val original = WsMessage.NewMessage(
            messageId = "m1",
            conversationId = "c1",
            senderId = "u1",
            senderName = "Ali",
            content = "",
            contentType = ContentType.IMAGE,
            mediaUrl = "https://cdn.example.com/image.jpg",
            thumbnailUrl = "https://cdn.example.com/thumb.jpg",
            serverTimestamp = 1707840000000L
        )
        val json = wsJson.encodeToString(WsMessage.serializer(), original)
        val decoded = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.NewMessage>(decoded)
        assertEquals(ContentType.IMAGE, decoded.contentType)
        assertEquals("https://cdn.example.com/image.jpg", decoded.mediaUrl)
    }

    @Test
    fun should_ignore_unknown_fields_gracefully() {
        // Ensure forward compatibility — new fields don't break old clients
        val json = """{"type":"message.new","messageId":"m1","conversationId":"c1",
            "senderId":"u1","senderName":null,"content":"Hi","contentType":"TEXT",
            "serverTimestamp":1707840000000,"futureField":"unknown"}"""
        val msg = wsJson.decodeFromString<WsMessage>(json)
        assertIs<WsMessage.NewMessage>(msg)
        assertEquals("Hi", msg.content)
    }
}

package com.muhabbet.shared.protocol

import com.muhabbet.shared.model.CallType
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.model.PresenceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins EVERY `@SerialName` `type` discriminator on the WS protocol to its exact string.
 *
 * Why this exists: the discriminator strings are the wire contract shared by backend, mobile, and
 * any external client/bot (see CLAUDE.md "WsMessage type discriminators"). A rename would silently
 * break cross-client routing without any compile error — only a test can catch it. The values below
 * are copied verbatim from CLAUDE.md; if a discriminator legitimately changes, BOTH must change
 * together (and old clients must be considered).
 */
class WsDiscriminatorContractTest {

    /** Extract the `"type":"..."` value from an encoded WsMessage. */
    private fun typeOf(msg: WsMessage): String {
        val json = wsJson.encodeToString(WsMessage.serializer(), msg)
        val marker = "\"type\":\""
        val start = json.indexOf(marker) + marker.length
        val end = json.indexOf('"', start)
        return json.substring(start, end)
    }

    @Test
    fun client_to_server_discriminators_are_pinned() {
        assertEquals("message.send", typeOf(
            WsMessage.SendMessage(requestId = "r", messageId = "m", conversationId = "c", content = "x")
        ))
        assertEquals("message.ack", typeOf(
            WsMessage.AckMessage(messageId = "m", conversationId = "c", status = MessageStatus.READ)
        ))
        assertEquals("presence.typing", typeOf(
            WsMessage.TypingIndicator(conversationId = "c", isTyping = true)
        ))
        assertEquals("presence.online", typeOf(WsMessage.GoOnline))
        assertEquals("ping", typeOf(WsMessage.Ping))
        assertEquals("auth.login_response", typeOf(
            WsMessage.LoginApprovalResponse(approvalId = "a", approved = true)
        ))
    }

    @Test
    fun call_signaling_discriminators_are_pinned() {
        assertEquals("call.initiate", typeOf(
            WsMessage.CallInitiate(callId = "c", targetUserId = "u", callType = CallType.VOICE)
        ))
        assertEquals("call.answer", typeOf(WsMessage.CallAnswer(callId = "c", accepted = true)))
        assertEquals("call.ice", typeOf(WsMessage.CallIceCandidate(callId = "c", candidate = "x")))
        assertEquals("call.end", typeOf(WsMessage.CallEnd(callId = "c")))
        assertEquals("call.incoming", typeOf(
            WsMessage.CallIncoming(callId = "c", callerId = "u", callerName = null, callType = CallType.VOICE)
        ))
        assertEquals("call.room", typeOf(
            WsMessage.CallRoomInfo(callId = "c", serverUrl = "s", token = "t", roomName = "r")
        ))
        assertEquals("call.group_start", typeOf(
            WsMessage.GroupCallStarted(
                callId = "c", conversationId = "cv", callerId = "u", callerName = null,
                callType = CallType.VIDEO, participantCount = 2
            )
        ))
    }

    @Test
    fun server_to_client_discriminators_are_pinned() {
        assertEquals("message.new", typeOf(
            WsMessage.NewMessage(
                messageId = "m", conversationId = "c", senderId = "u", senderName = null,
                content = "x", contentType = com.muhabbet.shared.model.ContentType.TEXT,
                serverTimestamp = 0L
            )
        ))
        assertEquals("message.status", typeOf(
            WsMessage.StatusUpdate(messageId = "m", conversationId = "c", userId = "u",
                status = MessageStatus.DELIVERED, timestamp = 0L)
        ))
        assertEquals("ack", typeOf(
            WsMessage.ServerAck(requestId = "r", messageId = "m", status = AckStatus.OK)
        ))
        assertEquals("presence.update", typeOf(
            WsMessage.PresenceUpdate(userId = "u", status = PresenceStatus.ONLINE)
        ))
        assertEquals("pong", typeOf(WsMessage.Pong))
        assertEquals("error", typeOf(WsMessage.Error(code = "X", message = "y")))
    }

    @Test
    fun group_and_message_event_discriminators_are_pinned() {
        assertEquals("group.member_added", typeOf(
            WsMessage.GroupMemberAdded(conversationId = "c", addedBy = "u", members = emptyList())
        ))
        assertEquals("group.member_removed", typeOf(
            WsMessage.GroupMemberRemoved(conversationId = "c", removedBy = "u", userId = "x")
        ))
        assertEquals("group.info_updated", typeOf(
            WsMessage.GroupInfoUpdated(conversationId = "c", updatedBy = "u")
        ))
        assertEquals("group.role_updated", typeOf(
            WsMessage.GroupRoleUpdated(conversationId = "c", updatedBy = "u", userId = "x", newRole = "ADMIN")
        ))
        assertEquals("group.member_left", typeOf(
            WsMessage.GroupMemberLeft(conversationId = "c", userId = "x")
        ))
        assertEquals("message.deleted", typeOf(
            WsMessage.MessageDeleted(messageId = "m", conversationId = "c", deletedBy = "u", timestamp = 0L)
        ))
        assertEquals("message.edited", typeOf(
            WsMessage.MessageEdited(messageId = "m", conversationId = "c", editedBy = "u",
                newContent = "x", editedAt = 0L)
        ))
        assertEquals("message.reaction", typeOf(
            WsMessage.MessageReaction(messageId = "m", conversationId = "c", userId = "u",
                emoji = "👍", action = "add")
        ))
        assertEquals("security.key_changed", typeOf(
            WsMessage.SecurityKeyChanged(userId = "u", conversationId = null, timestamp = 0L)
        ))
        assertEquals("auth.login_approval", typeOf(
            WsMessage.LoginApprovalRequest(approvalId = "a", deviceName = null, platform = null, timestamp = 0L)
        ))
    }

    @Test
    fun discriminator_field_name_is_type() {
        // wsJson is configured with classDiscriminator = "type"; verify it has not drifted.
        val json = wsJson.encodeToString(WsMessage.serializer(), WsMessage.Ping)
        assertTrue(json.contains("\"type\":"), "discriminator field must be `type`, got: $json")
    }
}

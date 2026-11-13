package com.muhabbet.shared.protocol

import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.dto.PrivacySettingsResponse
import com.muhabbet.shared.dto.SetWallpaperRequest
import com.muhabbet.shared.dto.UpdatePrivacyRequest
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.ChatActivityKind
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.MemberRole
import com.muhabbet.shared.model.ReactionAction
import com.muhabbet.shared.model.VisibilityLevel
import com.muhabbet.shared.model.WallpaperType
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The contract between an app on a phone and a server that has moved on without it (#873, #875,
 * #877, #881, #885, #887). Every test here decodes something a **newer** peer could send, or pins a
 * wire string an **older** peer depends on.
 */
class ForwardCompatibilityTest {

    private val json = MuhabbetJson

    // ─── Unknown frames (#873) ────────────────────────────

    @Test
    fun `should decode an unknown frame type to Unknown instead of throwing`() {
        val frame = json.decodeFromString<WsMessage>("""{"type":"future.frame","x":1}""")

        assertIs<WsMessage.Unknown>(frame)
        assertEquals("future.frame", frame.type)
        assertEquals("1", frame.raw["x"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should decode a frame with no type at all to Unknown with a null type`() {
        val frame = json.decodeFromString<WsMessage>("""{"x":1}""")

        assertIs<WsMessage.Unknown>(frame)
        assertNull(frame.type)
    }

    @Test
    fun `should still decode a known frame exactly as before`() {
        val frame = json.decodeFromString<WsMessage>("""{"type":"pong"}""")
        assertIs<WsMessage.Pong>(frame)
    }

    // ─── Lenient enums (#875) ─────────────────────────────

    @Test
    fun `should decode an unknown content type to UNKNOWN and keep the rest of the frame`() {
        val frame = json.decodeFromString<WsMessage>(
            """{"type":"message.new","messageId":"m1","conversationId":"c1","senderId":"u1",
               "content":"x","contentType":"HOLOGRAM","serverTimestamp":1}"""
        )

        assertIs<WsMessage.NewMessage>(frame)
        assertEquals(ContentType.UNKNOWN, frame.contentType)
        assertEquals("m1", frame.messageId)
    }

    @Test
    fun `should decode a conversation page whose participant has an unknown role`() {
        val page = json.decodeFromString<ConversationResponse>(
            """{"id":"c1","type":"GROUP","participants":[
                 {"userId":"u1","role":"MODERATOR","isOnline":false},
                 {"userId":"u2","role":"OWNER","isOnline":true}],
               "unreadCount":0,"createdAt":"2026-01-01T00:00:00Z"}"""
        )

        assertEquals(listOf(MemberRole.UNKNOWN, MemberRole.OWNER), page.participants.map { it.role })
    }

    @Test
    fun `should decode an unknown conversation type and an unknown optional content type`() {
        val page = json.decodeFromString<ConversationResponse>(
            """{"id":"c1","type":"BROADCAST","participants":[],"lastMessageContentType":"HOLOGRAM",
               "unreadCount":0,"createdAt":"2026-01-01T00:00:00Z"}"""
        )

        assertEquals(ContentType.UNKNOWN, page.lastMessageContentType)
    }

    @Test
    fun `should encode known enum values byte-identically to the names they always had`() {
        val frame = WsMessage.MessageReaction("m1", "c1", "u1", "x", ReactionAction.REMOVE)
        val encoded = json.encodeToString<WsMessage>(frame)

        assertTrue(""""action":"remove"""" in encoded, encoded)
        assertEquals(frame, json.decodeFromString<WsMessage>(encoded))
    }

    @Test
    fun `should decode an unknown error code to UNKNOWN rather than failing the envelope`() {
        val response = json.decodeFromString(
            ApiResponse.serializer(String.serializer()),
            """{"error":{"code":"SOMETHING_NEW","message":"x"},"timestamp":"t"}""",
        )

        assertEquals(ErrorCode.UNKNOWN, response.error?.code)
    }

    // ─── Typed wire fields keep their strings (#877) ─────

    @Test
    fun `should keep the lowercase privacy wire values`() {
        val encoded = json.encodeToString(
            UpdatePrivacyRequest(onlineStatusVisibility = VisibilityLevel.CONTACTS, aboutVisibility = VisibilityLevel.NOBODY)
        )
        assertTrue(""""onlineStatusVisibility":"contacts"""" in encoded, encoded)
        assertTrue(""""aboutVisibility":"nobody"""" in encoded, encoded)

        val decoded = json.decodeFromString<PrivacySettingsResponse>(
            """{"readReceiptsEnabled":true,"onlineStatusVisibility":"everyone","aboutVisibility":"contacts"}"""
        )
        assertEquals(VisibilityLevel.EVERYONE, decoded.onlineStatusVisibility)
        assertEquals(VisibilityLevel.CONTACTS, decoded.aboutVisibility)
    }

    @Test
    fun `should round-trip the gradient wallpaper the backend has always stored`() {
        val encoded = json.encodeToString(SetWallpaperRequest(wallpaperType = WallpaperType.GRADIENT))

        assertTrue(""""wallpaperType":"GRADIENT"""" in encoded, encoded)
        assertEquals(WallpaperType.GRADIENT, json.decodeFromString<SetWallpaperRequest>(encoded).wallpaperType)
    }

    @Test
    fun `should decode a role update and a member list with typed roles`() {
        val frame = json.decodeFromString<WsMessage>(
            """{"type":"group.role_updated","conversationId":"c1","updatedBy":"u1","userId":"u2","newRole":"ADMIN"}"""
        )
        assertIs<WsMessage.GroupRoleUpdated>(frame)
        assertEquals(MemberRole.ADMIN, frame.newRole)
    }

    // ─── Envelopes that cannot express illegal states (#881) ─

    @Test
    fun `should refuse an accepted ack without a server timestamp`() {
        assertFailsWith<IllegalArgumentException> {
            WsMessage.ServerAck("r", "m", AckStatus.OK, serverTimestamp = null)
        }
    }

    @Test
    fun `should refuse a rejected ack without an error code`() {
        assertFailsWith<IllegalArgumentException> {
            WsMessage.ServerAck("r", "m", AckStatus.ERROR, errorCode = null)
        }
    }

    @Test
    fun `should not write errorMessage on a rejected ack`() {
        val encoded = json.encodeToString<WsMessage>(WsMessage.ServerAck.rejected("r", "m", ErrorCode.MSG_NOT_MEMBER))

        assertTrue(""""errorCode":"MSG_NOT_MEMBER"""" in encoded, encoded)
        assertTrue("errorMessage" !in encoded, encoded)
    }

    // ─── Typing is not presence (#885) ──────────────────

    @Test
    fun `should round-trip chat activity under its own frame type`() {
        val frame = WsMessage.ChatActivity("c1", "u1", ChatActivityKind.RECORDING_AUDIO, ttlMs = 5_000)
        val encoded = json.encodeToString<WsMessage>(frame)

        assertTrue(""""type":"chat.activity"""" in encoded, encoded)
        assertTrue(""""activity":"RECORDING_AUDIO"""" in encoded, encoded)
        assertEquals(frame, json.decodeFromString<WsMessage>(encoded))
    }

    @Test
    fun `should decode chat activity without a ttl to the default`() {
        val frame = json.decodeFromString<WsMessage>(
            """{"type":"chat.activity","conversationId":"c1","userId":"u1","activity":"TYPING"}"""
        )
        assertIs<WsMessage.ChatActivity>(frame)
        assertEquals(WsMessage.ChatActivity.DEFAULT_TTL_MS, frame.ttlMs)
    }

    // ─── Omitted nulls (#887) ────────────────────────────

    @Test
    fun `should decode a last page whose nextCursor key is omitted`() {
        val page = json.decodeFromString(
            PaginatedResponse.serializer(String.serializer()),
            """{"items":["a"],"hasMore":false}""",
        )
        assertNull(page.nextCursor)
    }

    @Test
    fun `should decode a new message whose senderName key is omitted`() {
        val frame = json.decodeFromString<WsMessage>(
            """{"type":"message.new","messageId":"m1","conversationId":"c1","senderId":"u1",
               "content":"x","contentType":"TEXT","serverTimestamp":1}"""
        )
        assertIs<WsMessage.NewMessage>(frame)
        assertNull(frame.senderName)
    }
}

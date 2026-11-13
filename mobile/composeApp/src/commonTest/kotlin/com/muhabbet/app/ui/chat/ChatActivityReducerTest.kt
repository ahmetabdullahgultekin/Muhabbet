package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.ChatActivityKind
import com.muhabbet.shared.protocol.WsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatActivityReducerTest {

    private val conversation = "c1"
    private val me = "me"

    private fun frame(user: String, kind: ChatActivityKind, ttl: Long = 5_000, conv: String = conversation) =
        WsMessage.ChatActivity(conversationId = conv, userId = user, activity = kind, ttlMs = ttl)

    private fun Map<String, MemberActivity>.apply(f: WsMessage.ChatActivity, now: Long = 0) =
        ChatActivityReducer.reduce(this, f, conversation, me, now)

    @Test
    fun `should still show B typing when A goes idle`() {
        val state = emptyMap<String, MemberActivity>()
            .apply(frame("A", ChatActivityKind.TYPING))
            .apply(frame("B", ChatActivityKind.TYPING))
            .apply(frame("A", ChatActivityKind.IDLE))

        assertEquals(setOf("B"), state.keys)
    }

    @Test
    fun `should expire only the member whose activity was not renewed`() {
        val state = emptyMap<String, MemberActivity>()
            .apply(frame("A", ChatActivityKind.TYPING, ttl = 1_000), now = 0)
            .apply(frame("B", ChatActivityKind.TYPING, ttl = 1_000), now = 800)

        val after = ChatActivityReducer.expire(state, nowMs = 1_200)

        assertEquals(setOf("B"), after.keys)
    }

    @Test
    fun `should renew a member's deadline when they keep typing`() {
        val state = emptyMap<String, MemberActivity>()
            .apply(frame("A", ChatActivityKind.TYPING, ttl = 1_000), now = 0)
            .apply(frame("A", ChatActivityKind.TYPING, ttl = 1_000), now = 900)

        assertEquals(setOf("A"), ChatActivityReducer.expire(state, nowMs = 1_500).keys)
    }

    @Test
    fun `should keep recording audio distinct from typing`() {
        val state = emptyMap<String, MemberActivity>().apply(frame("A", ChatActivityKind.RECORDING_AUDIO))

        assertEquals(ChatActivityKind.RECORDING_AUDIO, state.getValue("A").kind)
    }

    @Test
    fun `should ignore another conversation, this user, and an activity this build does not know`() {
        val state = emptyMap<String, MemberActivity>()
            .apply(frame("A", ChatActivityKind.TYPING, conv = "elsewhere"))
            .apply(frame(me, ChatActivityKind.TYPING))
            .apply(frame("B", ChatActivityKind.UNKNOWN))

        assertTrue(state.isEmpty())
    }

    @Test
    fun `should report the earliest deadline, or none`() {
        assertNull(ChatActivityReducer.nextExpiryMs(emptyMap()))

        val state = emptyMap<String, MemberActivity>()
            .apply(frame("A", ChatActivityKind.TYPING, ttl = 3_000))
            .apply(frame("B", ChatActivityKind.TYPING, ttl = 1_000))

        assertEquals(1_000, ChatActivityReducer.nextExpiryMs(state))
    }
}

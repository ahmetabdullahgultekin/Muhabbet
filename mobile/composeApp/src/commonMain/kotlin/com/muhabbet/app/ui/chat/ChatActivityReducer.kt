package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.ChatActivityKind
import com.muhabbet.shared.protocol.WsMessage

/**
 * What one other member is doing in the open conversation, and until when it is believed.
 */
data class MemberActivity(val kind: ChatActivityKind, val expiresAtMs: Long)

/**
 * Who is typing (or recording) in one conversation — a map keyed by member, reduced by pure
 * functions (#885).
 *
 * It replaced a single `peerTyping` boolean that any update from any member flipped. In a group that
 * meant A stopping hid the indicator while B was still typing, and there was no way to say who.
 * Keyed per member, A's `IDLE` removes A and nothing else; and each entry carries its own deadline,
 * so a lost `IDLE` (a dropped socket, a killed app) expires that one member instead of leaving them
 * typing forever — or, as the old shared timer did, clearing everybody at once.
 */
object ChatActivityReducer {

    /**
     * Applies one `chat.activity` frame. Frames for another conversation, from this user, or of a
     * kind this build does not know are ignored: an unknown activity is not evidence of typing.
     */
    fun reduce(
        state: Map<String, MemberActivity>,
        frame: WsMessage.ChatActivity,
        conversationId: String,
        selfUserId: String?,
        nowMs: Long,
    ): Map<String, MemberActivity> {
        if (frame.conversationId != conversationId || frame.userId == selfUserId) return state
        return when (frame.activity) {
            ChatActivityKind.TYPING, ChatActivityKind.RECORDING_AUDIO ->
                state + (frame.userId to MemberActivity(frame.activity, nowMs + frame.ttlMs))
            ChatActivityKind.IDLE -> state - frame.userId
            ChatActivityKind.UNKNOWN -> state
        }
    }

    /** Drops every member whose activity was not renewed before its deadline. */
    fun expire(state: Map<String, MemberActivity>, nowMs: Long): Map<String, MemberActivity> =
        state.filterValues { it.expiresAtMs > nowMs }

    /** When the next entry lapses, or null when nobody is doing anything. */
    fun nextExpiryMs(state: Map<String, MemberActivity>): Long? = state.values.minOfOrNull { it.expiresAtMs }
}

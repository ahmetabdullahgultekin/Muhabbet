package com.muhabbet.app.ui.conversations

/**
 * Everything needed to open a chat, resolved by whoever was already displaying the conversation.
 *
 * This replaces a five-positional-argument lambda that four different call sites each assembled by
 * hand — the list row, the message-search results, the home shell's search results, and the
 * navigation layer. At four arguments that was survivable; adding [avatarUrl] would have made
 * `(String, String, String?, Boolean, String?)` a shape nobody can read at the call site, and two
 * adjacent nullable strings is an argument-swap waiting to happen.
 *
 * The resolution genuinely belongs to the caller rather than to the chat screen: which of several
 * candidate names wins (group name, local contact name, display name, phone number) depends on the
 * contact map the list already has loaded, and [avatarUrl] is the group's picture for a group and
 * the other participant's for a DM.
 *
 * @param name already resolved for display. The chat screen shows it verbatim.
 * @param otherUserId null for groups; the DM partner otherwise.
 * @param avatarUrl null when there is no photo, in which case the avatar falls back to the
 *   name-seeded gradient. Carried through navigation rather than re-fetched so that it is present
 *   on the chat screen's first frame — a title bar whose avatar pops in a moment late is worse than
 *   one that was never animated.
 */
data class ChatTarget(
    val conversationId: String,
    val name: String,
    val otherUserId: String? = null,
    val isGroup: Boolean = false,
    val avatarUrl: String? = null
)

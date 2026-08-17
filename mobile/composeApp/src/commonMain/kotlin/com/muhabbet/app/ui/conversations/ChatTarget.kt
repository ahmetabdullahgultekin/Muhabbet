package com.muhabbet.app.ui.conversations

import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.ConversationType

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

/**
 * The one rule for what a conversation is called and whose face it wears.
 *
 * Four screens each wrote their own version of this, and the versions did not agree. Two of them
 * ended `?: ""` — the message-search results and the home shell's search results — and one caller
 * skipped the resolution entirely and passed `""` outright (#543: opening a chat from Starred
 * Messages landed on a chat with no title, a "?" avatar and a dead tap on the person, because
 * `ChatScreen` renders [name] verbatim and has no fallback of its own).
 *
 * So the empty string is gone from the type's vocabulary. There is always a [fallbackName] — a
 * localized one, from `composeResources` — and the worst case is a chat titled "Sohbet" rather
 * than a chat titled nothing.
 *
 * @param currentUserId whose participant row to skip. Nullable because [com.muhabbet.app.data.local.TokenStorage]
 *   can answer null; a null here just means no row is skipped, which for a direct conversation
 *   picks the caller half the time — recoverable, where crashing or blanking is not.
 * @param fallbackName shown when nothing else resolves. Never pass `""`.
 * @param contactNames local address-book names keyed by **phone number**, as the conversation list
 *   already assembles them. Empty for screens that never loaded contacts, which is why it defaults:
 *   the address book is a nicety here, not a precondition.
 */
fun ConversationResponse.toChatTarget(
    currentUserId: String?,
    fallbackName: String,
    contactNames: Map<String, String> = emptyMap()
): ChatTarget {
    val isGroup = type == ConversationType.GROUP
    val other = participants.firstOrNull { it.userId != currentUserId }
    return ChatTarget(
        conversationId = id,
        // A group falls straight to the fallback rather than to a member's name. Titling a group
        // with whichever member happened to sort first is the wrong identity, not a shorter one.
        name = name?.ifBlank { null }
            ?: (if (isGroup) null else other?.label(contactNames))
            ?: fallbackName,
        otherUserId = if (isGroup) null else other?.userId,
        isGroup = isGroup,
        avatarUrl = if (isGroup) avatarUrl else other?.avatarUrl
    )
}

/**
 * What to call the person who sent a message in this conversation, or null if they are not a
 * participant of it.
 *
 * Null is a real answer and is deliberately not collapsed to a constant here: a screen showing a
 * message from someone who has since left needs to say so in its own words, and the caller owns
 * that string. Returning "Unknown contact" from a resolver is how #543's starred list ended up
 * labelling *everyone* that way.
 */
fun ConversationResponse.senderLabel(
    senderId: String,
    contactNames: Map<String, String> = emptyMap()
): String? = participants.firstOrNull { it.userId == senderId }?.label(contactNames)

/**
 * Address-book name, then the name they chose, then their number.
 *
 * The address book wins because it is what the user themselves wrote down, and a display name is
 * whatever the other side typed. A bare user id is never a candidate — its first eight characters
 * read as a hex hash, which is exactly what #507 shipped.
 *
 * Blank is treated as absent at every rung. The server validates `displayName` with `isNotBlank()`,
 * so `""` should never arrive — but "should never arrive" is the assumption this whole change exists
 * to stop relying on, and a `?:` chain happily carries an empty string all the way to the title bar.
 */
private fun ParticipantResponse.label(contactNames: Map<String, String>): String? =
    phoneNumber?.let { contactNames[it] }?.ifBlank { null }
        ?: displayName?.ifBlank { null }
        ?: phoneNumber?.ifBlank { null }

package com.muhabbet.app.ui.conversations

import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType

/**
 * What the conversation list puts on screen, decided outside the composable that draws it.
 *
 * Pulled out for #612. The archived row carries a count and leads somewhere; if the count and the
 * screen behind it were derived by two rules written in two files, they could disagree, and a row
 * saying "3" that opens onto an empty screen is a worse lie than the invisible section it replaced.
 * One function, one rule, and a test that can state the rule without instantiating Compose.
 */
internal data class ConversationSections(
    /** The main list: never archived, pinned first, then newest first. */
    val active: List<ConversationResponse>,
    /** How many conversations are archived. */
    val archivedCount: Int
)

/**
 * Splits [conversations] into what the main list shows and how many are archived.
 *
 * [filter] applies to the active list only. The archived count deliberately ignores it: it is a
 * standing "how many are in the archive", not "how many archived chats are also unread". Running it
 * through the chips would let a filter tap make the row vanish, which is the same
 * disappears-when-you-are-not-looking defect this row exists to remove — just triggered by a tap
 * instead of an empty archive.
 */
internal fun conversationSections(
    conversations: List<ConversationResponse>,
    filter: ConversationFilter
): ConversationSections {
    val filtered = when (filter) {
        ConversationFilter.UNREAD -> conversations.filter { it.unreadCount > 0 }
        ConversationFilter.FAVORITES -> conversations.filter { it.isPinned }
        ConversationFilter.GROUPS -> conversations.filter { it.type == ConversationType.GROUP }
        ConversationFilter.ALL -> conversations
    }
    return ConversationSections(
        active = filtered
            .filterNot { it.isArchived }
            .sortedWith(
                compareByDescending<ConversationResponse> { it.isPinned }
                    .thenByDescending { it.lastMessageAt ?: "" }
            ),
        archivedCount = conversations.count { it.isArchived }
    )
}

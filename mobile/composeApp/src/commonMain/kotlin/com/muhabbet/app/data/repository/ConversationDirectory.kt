package com.muhabbet.app.data.repository

import com.muhabbet.shared.dto.ConversationResponse

/**
 * Look conversations up by id, for screens that hold a conversation id and nothing else.
 *
 * A `Message` carries a `conversationId` and a `senderId`; it carries no conversation name, no
 * avatar and no sender name. Starred Messages, message search and (soon) the jump-to-original of a
 * forwarded message all start from one of those ids and need the rest before they can draw a title
 * bar or say who spoke. Until #543 the starred screen simply gave up and navigated with `""`.
 *
 * **Not [KnownPeopleSource], deliberately.** That class answers "whom may a picker offer", and its
 * direct-conversations-only scoping is a privacy boundary defended by #507 and #425 — group
 * co-members are excluded from it on purpose. Reusing it here would leave every sender in a group
 * chat unnamed, and widening it to include them would reopen #507 for every picker in the app. This
 * asks a narrower question with a narrower answer: it returns conversations the caller already named
 * by id, and never a browsable list of people.
 *
 * The naming rule itself is not here — it is
 * [com.muhabbet.app.ui.conversations.toChatTarget]. This class does the paging; that function does
 * the deciding.
 */
class ConversationDirectory(
    private val conversationRepository: ConversationRepository
) {

    /**
     * @return the subset of [conversationIds] that could be found, keyed by id. An id absent from
     *   the result means "not found", which the caller must render honestly — the conversation may
     *   have been deleted or left, or simply sit past [MAX_PAGES] of history.
     *
     * Stops as soon as every id is accounted for, so the common case (a handful of starred messages
     * from the conversations at the top of the list) costs exactly one request. [MAX_PAGES] caps the
     * pathological case: an id that does not exist would otherwise walk the user's entire history
     * looking for it, one request at a time.
     */
    suspend fun lookUp(conversationIds: Set<String>): Map<String, ConversationResponse> {
        if (conversationIds.isEmpty()) return emptyMap()
        val found = mutableMapOf<String, ConversationResponse>()
        var cursor: String? = null
        var pagesRead = 0
        while (pagesRead < MAX_PAGES) {
            val page = conversationRepository.getConversations(cursor = cursor, limit = PAGE_SIZE)
            page.items.forEach { conversation ->
                if (conversation.id in conversationIds) found[conversation.id] = conversation
            }
            pagesRead++
            if (found.size == conversationIds.size) break
            cursor = page.nextCursor ?: break
        }
        return found
    }

    internal companion object {
        const val PAGE_SIZE = 50

        /**
         * Five pages of 50. Past that the answer is "not on this device right now", and saying so is
         * better than an unbounded walk the user watches as a spinner.
         */
        const val MAX_PAGES = 5
    }
}

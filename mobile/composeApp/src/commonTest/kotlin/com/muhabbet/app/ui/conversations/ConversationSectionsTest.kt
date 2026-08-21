package com.muhabbet.app.ui.conversations

import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the conversation list shows and what the archived row claims (#612).
 *
 * The issue was that archiving wrote a row and changed nothing on screen. The half that is testable
 * without a device is this one: an archived chat must leave the main list, and the count the row
 * advertises must be the number of chats the archived screen will then find. Both come from
 * [conversationSections], so both are stated here.
 */
class ConversationSectionsTest {

    private fun conversation(
        id: String,
        archived: Boolean = false,
        pinned: Boolean = false,
        unread: Int = 0,
        type: ConversationType = ConversationType.DIRECT,
        lastMessageAt: String? = null
    ) = ConversationResponse(
        id = id,
        type = type,
        participants = emptyList(),
        unreadCount = unread,
        createdAt = "2026-08-21T00:00:00Z",
        isPinned = pinned,
        isArchived = archived,
        lastMessageAt = lastMessageAt
    )

    @Test
    fun should_keep_archived_chats_out_of_the_main_list() {
        val sections = conversationSections(
            listOf(conversation("a"), conversation("b", archived = true)),
            ConversationFilter.ALL
        )

        assertEquals(listOf("a"), sections.active.map { it.id })
        assertEquals(1, sections.archivedCount)
    }

    @Test
    fun should_count_the_archive_whatever_the_filter_says() {
        // The archived chat is read, so the Unread chip excludes it. The row must still say 1 —
        // a count that a filter tap can zero out is the same vanishing act the row exists to end.
        val conversations = listOf(
            conversation("unread-active", unread = 3),
            conversation("read-archived", archived = true)
        )

        ConversationFilter.entries.forEach { filter ->
            assertEquals(
                1,
                conversationSections(conversations, filter).archivedCount,
                "The archive count must not depend on the $filter chip."
            )
        }
    }

    @Test
    fun should_apply_the_filter_to_the_active_list() {
        val sections = conversationSections(
            listOf(
                conversation("group", type = ConversationType.GROUP),
                conversation("direct"),
                conversation("archived-group", type = ConversationType.GROUP, archived = true)
            ),
            ConversationFilter.GROUPS
        )

        assertEquals(
            listOf("group"),
            sections.active.map { it.id },
            "GROUPS keeps groups, and archived stays out even when it matches the chip."
        )
    }

    @Test
    fun should_put_pinned_chats_first_then_the_most_recent() {
        val sections = conversationSections(
            listOf(
                conversation("old", lastMessageAt = "2026-08-01T00:00:00Z"),
                conversation("new", lastMessageAt = "2026-08-20T00:00:00Z"),
                conversation("pinned-old", pinned = true, lastMessageAt = "2026-07-01T00:00:00Z")
            ),
            ConversationFilter.ALL
        )

        assertEquals(listOf("pinned-old", "new", "old"), sections.active.map { it.id })
    }

    @Test
    fun should_report_an_empty_archive_when_nothing_is_archived() {
        val sections = conversationSections(listOf(conversation("a")), ConversationFilter.ALL)

        assertEquals(0, sections.archivedCount, "Nothing archived means no row at all.")
        assertTrue(sections.active.isNotEmpty())
    }
}

package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.ContentType
import java.util.UUID

interface GetConversationsUseCase {
    fun getConversations(userId: UUID, cursor: String?, limit: Int): ConversationPage
}

data class ConversationSummary(
    val conversationId: UUID,
    val type: String,
    val name: String?,
    val avatarUrl: String?,
    val lastMessagePreview: String?,
    /**
     * The last message's kind, so the reading device can name it in the reader's own language.
     *
     * [lastMessagePreview] is the message body and nothing else. For a photo or a voice note the
     * body carried the *word* "Photo", written by the sender's app in the sender's language and
     * stored here forever (#534) — a preview that no amount of switching language on the reading
     * device could correct, because it was never that device's string.
     */
    val lastMessageContentType: ContentType?,
    val lastMessageAt: String?,
    val unreadCount: Int,
    val participantIds: List<UUID>,
    val disappearAfterSeconds: Int? = null,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val isLocked: Boolean = false
)

data class ConversationPage(
    val items: List<ConversationSummary>,
    val nextCursor: String?,
    val hasMore: Boolean
)

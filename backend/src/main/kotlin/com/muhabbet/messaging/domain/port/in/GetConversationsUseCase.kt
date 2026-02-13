package com.muhabbet.messaging.domain.port.`in`

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
    val lastMessageAt: String?,
    val unreadCount: Int,
    val participantIds: List<UUID>,
    val disappearAfterSeconds: Int? = null,
    val isPinned: Boolean = false
)

data class ConversationPage(
    val items: List<ConversationSummary>,
    val nextCursor: String?,
    val hasMore: Boolean
)

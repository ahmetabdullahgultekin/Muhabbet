package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

interface GetMessageHistoryUseCase {
    fun getMessages(
        conversationId: UUID,
        userId: UUID,
        cursor: String?,
        limit: Int,
        direction: String = "before"
    ): MessagePage
}

data class MessagePage(
    val items: List<Message>,
    val nextCursor: String?,
    val hasMore: Boolean
)

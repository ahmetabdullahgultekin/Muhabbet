package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

interface ManageMessageUseCase {
    fun deleteMessage(messageId: UUID, requesterId: UUID)
    fun editMessage(messageId: UUID, requesterId: UUID, newContent: String): Message

    /**
     * Burns a view-once message for [userId]. Membership is authorized behind this port, so a
     * non-member who guesses a messageId cannot destroy someone else's message.
     */
    fun markViewOnceViewed(messageId: UUID, userId: UUID)
}

package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

interface ManageMessageUseCase {
    fun deleteMessage(messageId: UUID, requesterId: UUID)
    fun editMessage(messageId: UUID, requesterId: UUID, newContent: String): Message
}

package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.PinnedMessage
import java.util.UUID

interface ManagePinnedMessageUseCase {
    fun pin(conversationId: UUID, messageId: UUID, userId: UUID): PinnedMessage
    fun unpin(conversationId: UUID, messageId: UUID, userId: UUID)
    fun getPinned(conversationId: UUID, userId: UUID): List<PinnedMessage>
}

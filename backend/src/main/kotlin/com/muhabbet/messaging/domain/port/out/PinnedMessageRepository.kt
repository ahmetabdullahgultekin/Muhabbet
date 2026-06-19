package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.PinnedMessage
import java.util.UUID

interface PinnedMessageRepository {
    fun save(pin: PinnedMessage): PinnedMessage
    fun find(conversationId: UUID, messageId: UUID): PinnedMessage?
    fun delete(conversationId: UUID, messageId: UUID)
    fun findByConversationId(conversationId: UUID): List<PinnedMessage>
    fun countByConversationId(conversationId: UUID): Long
}

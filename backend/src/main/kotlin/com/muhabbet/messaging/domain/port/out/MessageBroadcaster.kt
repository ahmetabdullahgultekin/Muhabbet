package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

interface MessageBroadcaster {
    fun broadcastMessage(message: Message, recipientIds: List<UUID>)
    fun broadcastStatusUpdate(messageId: UUID, conversationId: UUID, userId: UUID, status: DeliveryStatus)
}

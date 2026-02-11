package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.DeliveryStatus
import java.util.UUID

interface UpdateDeliveryStatusUseCase {
    fun updateStatus(messageId: UUID, userId: UUID, status: DeliveryStatus)
    fun markConversationRead(conversationId: UUID, userId: UUID)
}

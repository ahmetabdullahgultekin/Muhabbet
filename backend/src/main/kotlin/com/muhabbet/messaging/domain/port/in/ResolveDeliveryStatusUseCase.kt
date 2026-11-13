package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

interface ResolveDeliveryStatusUseCase {
    /**
     * Resolves the aggregate delivery status for a list of messages from the perspective of the requesting user.
     * - Sender perspective: aggregate = min across all recipients (all READ → READ, any DELIVERED/READ → DELIVERED, else SENT)
     * - Recipient perspective: their own status row
     */
    fun resolveDeliveryStatuses(messages: List<Message>, requestingUserId: UUID): Map<UUID, DeliveryStatus>
}

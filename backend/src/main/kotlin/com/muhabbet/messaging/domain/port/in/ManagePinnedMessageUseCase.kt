package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.PinnedMessage
import com.muhabbet.shared.dto.PinnedMessageInfo
import java.util.UUID

interface ManagePinnedMessageUseCase {
    /**
     * Pins and, when something changed, tells every member the conversation's new set of pins as a
     * `message.pinned` snapshot (#790). Throws MSG_PIN_LIMIT_REACHED (409) on a fourth pin.
     */
    fun pin(conversationId: UUID, messageId: UUID, userId: UUID): PinnedMessage
    fun unpin(conversationId: UUID, messageId: UUID, userId: UUID)
    fun getPinned(conversationId: UUID, userId: UUID): List<PinnedMessage>
}

/** The one mapping from a pin to its wire form, used by the REST response and the WS snapshot. */
fun PinnedMessage.toInfo(): PinnedMessageInfo = PinnedMessageInfo(
    conversationId = conversationId.toString(),
    messageId = messageId.toString(),
    pinnedBy = pinnedBy.toString(),
    pinnedAt = pinnedAt.toString()
)

package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.messaging.domain.model.Receipt
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import java.util.UUID

/**
 * The wire's `message.ack` as a domain [Receipt].
 *
 * `AckMessage.status` is the shared [MessageStatus], which can also spell `SENDING` and `SENT` —
 * states a client never acknowledges. Those were dropped with a bare `return` (#879), so a client
 * sending one learned nothing and its author had nothing to debug from. They are now refused with
 * [ErrorCode.MSG_INVALID_RECEIPT_STATUS], which the frame dispatcher answers as an error frame.
 *
 * Allow-list, not deny-list: only DELIVERED and READ are receipts, so anything else the shared enum
 * spells now or later (a client-side outbox state such as a failed send) is refused by default
 * rather than silently treated as one.
 */
internal fun WsMessage.AckMessage.toReceipt(readerId: UUID, messageId: UUID, conversationId: UUID): Receipt =
    when (status) {
        MessageStatus.DELIVERED -> Receipt.Delivered(messageId = messageId, readerId = readerId)
        MessageStatus.READ -> Receipt.Read(
            conversationId = conversationId,
            upToMessageId = messageId,
            readerId = readerId
        )
        else -> throw BusinessException(ErrorCode.MSG_INVALID_RECEIPT_STATUS)
    }

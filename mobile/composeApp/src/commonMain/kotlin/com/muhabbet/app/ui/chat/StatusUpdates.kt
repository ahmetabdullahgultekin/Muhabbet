package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage

/**
 * The sender's bubbles after a `message.status` frame, with each tick only ever moving forward.
 *
 * READ marks every one of my messages still at SENT or DELIVERED as read — reading a chat reads
 * everything above it. Any other status applies to the one message it names, and only if it is
 * ahead of what the bubble already shows. That second rule is what keeps a late DELIVERED from
 * pulling a READ bubble back to grey: the recipient acks DELIVERED only after the message is on
 * disk (#753), so with the chat open its READ routinely arrives first.
 */
fun List<Message>.withStatusUpdate(update: WsMessage.StatusUpdate, currentUserId: String): List<Message> =
    if (update.status == MessageStatus.READ) {
        map { m ->
            if (m.senderId == currentUserId && m.status in READABLE) m.copy(status = MessageStatus.READ) else m
        }
    } else {
        map { m -> if (m.id == update.messageId && update.status > m.status) m.copy(status = update.status) else m }
    }

private val READABLE = setOf(MessageStatus.SENT, MessageStatus.DELIVERED)

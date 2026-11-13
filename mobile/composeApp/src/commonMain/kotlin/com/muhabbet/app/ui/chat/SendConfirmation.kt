package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus

/**
 * Whether an accepting ServerAck for [messageId] is the moment one of this screen's own sends was
 * confirmed — the single point `MuhabbetHapticIntent.MessageSent` is allowed to fire (#836).
 *
 * Only a message still in [MessageStatus.SENDING] counts. That is what makes the haptic fire once
 * per message: the screen moves the message to SENT on the same ack, so a second ack for the id —
 * the offline queue resending after a reconnect and being answered `MSG_DUPLICATE` — finds nothing
 * pending. An ack for a message this screen is not showing (sent from another chat, or drained
 * from the queue before the list loaded) is someone else's confirmation and stays silent too.
 *
 * Call it with the list as it was *before* the ack is applied.
 */
internal fun ackConfirmsPendingSend(messages: List<Message>, messageId: String): Boolean =
    messages.any { it.id == messageId && it.status == MessageStatus.SENDING }

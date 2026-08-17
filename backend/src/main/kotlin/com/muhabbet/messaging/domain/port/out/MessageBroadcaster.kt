package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.shared.protocol.WsMessage
import java.util.UUID

interface MessageBroadcaster {
    /**
     * [recipients] carries each member's own row — not just their id — because #571 needs
     * [ConversationMember.mutedUntil] at exactly this point: an implementation must delivery a
     * connected recipient over the socket regardless of mute, and must withhold only the
     * offline-push courtesy from a recipient whose own mute is still in effect. The caller
     * (`MessageService`) already loaded this list for the block check and the delivery rows, so
     * this rides along rather than costing a second query per recipient (#491/#492).
     */
    fun broadcastMessage(message: Message, recipients: List<ConversationMember>)
    fun broadcastStatusUpdate(messageId: UUID, conversationId: UUID, readerId: UUID, senderId: UUID, status: DeliveryStatus)
    fun broadcastToUsers(recipientIds: List<UUID>, message: WsMessage)
}

package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import java.time.Instant
import java.util.UUID

interface MessageRepository {
    fun save(message: Message): Message
    fun findById(id: UUID): Message?
    fun existsById(id: UUID): Boolean
    fun findByConversationId(
        conversationId: UUID,
        before: Instant?,
        limit: Int
    ): List<Message>

    fun findUndeliveredForUser(userId: UUID, since: Instant?): List<Message>
    fun saveDeliveryStatus(status: MessageDeliveryStatus)
    fun updateDeliveryStatus(messageId: UUID, userId: UUID, status: DeliveryStatus)
    fun markConversationRead(conversationId: UUID, userId: UUID)
    fun getUnreadCount(conversationId: UUID, userId: UUID): Int
    fun getLastMessage(conversationId: UUID): Message?

    // Message management
    fun softDelete(messageId: UUID)
    fun updateContent(messageId: UUID, newContent: String, editedAt: Instant)

    // Media count
    fun countMediaInConversation(conversationId: UUID): Int

    // Batch delivery status lookup
    fun getDeliveryStatuses(messageIds: List<UUID>): List<MessageDeliveryStatus>

    // Media messages for shared media screen
    fun findMediaByConversationId(conversationId: UUID, limit: Int, offset: Int): List<Message>
}

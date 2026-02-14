package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageDeliveryStatusJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.MessageJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageDeliveryStatusRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageRepository
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.port.out.MessageRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class MessagePersistenceAdapter(
    private val messageRepo: SpringDataMessageRepository,
    private val deliveryStatusRepo: SpringDataMessageDeliveryStatusRepository
) : MessageRepository {

    override fun save(message: Message): Message =
        messageRepo.save(MessageJpaEntity.fromDomain(message)).toDomain()

    override fun findById(id: UUID): Message? =
        messageRepo.findById(id).orElse(null)?.toDomain()

    override fun existsById(id: UUID): Boolean =
        messageRepo.existsById(id)

    override fun findByConversationId(conversationId: UUID, before: Instant?, limit: Int): List<Message> {
        val pageable = PageRequest.of(0, limit)
        val entities = if (before != null) {
            messageRepo.findByConversationIdBefore(conversationId, before, pageable)
        } else {
            messageRepo.findByConversationIdLatest(conversationId, pageable)
        }
        return entities.map { it.toDomain() }
    }

    override fun findUndeliveredForUser(userId: UUID, since: Instant?): List<Message> {
        val sinceTime = since ?: Instant.now().minusSeconds(7 * 24 * 3600) // default: last 7 days
        val pageable = PageRequest.of(0, 500) // cap at 500 messages per sync
        return messageRepo.findMessagesSince(userId, sinceTime, pageable)
            .map { it.toDomain() }
    }

    override fun saveDeliveryStatus(status: MessageDeliveryStatus) {
        deliveryStatusRepo.save(MessageDeliveryStatusJpaEntity.fromDomain(status))
    }

    override fun updateDeliveryStatus(messageId: UUID, userId: UUID, status: DeliveryStatus) {
        val entity = deliveryStatusRepo.findByMessageIdAndUserId(messageId, userId) ?: return
        entity.status = status
        entity.updatedAt = Instant.now()
        deliveryStatusRepo.save(entity)
    }

    override fun markConversationRead(conversationId: UUID, userId: UUID) {
        deliveryStatusRepo.markAllAsRead(conversationId, userId, DeliveryStatus.READ, Instant.now())
    }

    override fun getUnreadCount(conversationId: UUID, userId: UUID): Int =
        deliveryStatusRepo.countUnread(conversationId, userId, DeliveryStatus.READ)

    override fun getLastMessage(conversationId: UUID): Message? =
        messageRepo.findLastByConversationId(conversationId)?.toDomain()

    override fun getLastMessages(conversationIds: List<UUID>): Map<UUID, Message> {
        if (conversationIds.isEmpty()) return emptyMap()
        return messageRepo.findLastMessagesByConversationIds(conversationIds)
            .associate { it.conversationId to it.toDomain() }
    }

    override fun getUnreadCounts(conversationIds: List<UUID>, userId: UUID): Map<UUID, Int> {
        if (conversationIds.isEmpty()) return emptyMap()
        return deliveryStatusRepo.countUnreadByConversations(conversationIds, userId, DeliveryStatus.READ)
            .associate { row -> (row[0] as UUID) to (row[1] as Long).toInt() }
    }

    override fun softDelete(messageId: UUID) {
        messageRepo.softDelete(messageId)
    }

    override fun updateContent(messageId: UUID, newContent: String, editedAt: Instant) {
        messageRepo.updateContent(messageId, newContent, editedAt)
    }

    override fun countMediaInConversation(conversationId: UUID): Int =
        messageRepo.countMediaInConversation(conversationId)

    override fun getDeliveryStatuses(messageIds: List<UUID>): List<MessageDeliveryStatus> {
        if (messageIds.isEmpty()) return emptyList()
        return deliveryStatusRepo.findByMessageIdIn(messageIds).map { it.toDomain() }
    }

    override fun findMediaByConversationId(conversationId: UUID, limit: Int, offset: Int): List<Message> {
        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit)
        val contentTypes = listOf(
            com.muhabbet.messaging.domain.model.ContentType.IMAGE,
            com.muhabbet.messaging.domain.model.ContentType.VIDEO,
            com.muhabbet.messaging.domain.model.ContentType.DOCUMENT,
            com.muhabbet.messaging.domain.model.ContentType.VOICE
        )
        return messageRepo.findMediaByConversationId(conversationId, contentTypes, pageable).map { it.toDomain() }
    }
}

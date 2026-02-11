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

    override fun findByConversationId(conversationId: UUID, before: Instant?, limit: Int): List<Message> =
        messageRepo.findByConversationIdBefore(conversationId, before, PageRequest.of(0, limit))
            .map { it.toDomain() }

    override fun findUndeliveredForUser(userId: UUID, since: Instant?): List<Message> {
        // This will be used by WebSocket reconnect — implemented when WS is added
        return emptyList()
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

    override fun getUnreadCount(conversationId: UUID, userId: UUID): Int =
        deliveryStatusRepo.countUnread(conversationId, userId)

    override fun getLastMessage(conversationId: UUID): Message? =
        messageRepo.findLastByConversationId(conversationId)?.toDomain()
}

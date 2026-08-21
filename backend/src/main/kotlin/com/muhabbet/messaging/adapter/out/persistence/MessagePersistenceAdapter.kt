package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageDeliveryStatusJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.MessageJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageDeliveryStatusRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageRepository
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.port.out.MessageRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class MessagePersistenceAdapter(
    private val messageRepo: SpringDataMessageRepository,
    private val deliveryStatusRepo: SpringDataMessageDeliveryStatusRepository,
    @PersistenceContext private val entityManager: EntityManager
) : MessageRepository {

    /**
     * `entityManager.persist`, not `repository.save`, and this is the whole of #492.
     *
     * `SimpleJpaRepository.save` decides between `persist` and `merge` by asking whether the id is
     * null. Every id in this application is assigned — the client mints the message id — so the
     * answer is always "not new" and every save was a `merge`. A merge must read the row it is
     * about to copy onto, so each of these inserts was preceded by a SELECT that could only ever
     * return nothing. That is one wasted round trip for the message and one for every recipient's
     * delivery row, and it also defeated `hibernate.jdbc.batch_size`: the interleaved SELECTs force
     * a flush between inserts, so nothing ever batched.
     *
     * The alternative was `Persistable<UUID>` with a transient `isNew` flag on the entities. It was
     * rejected: `Persistable.getId()` collides with the JVM signature Kotlin already generates for
     * the `id` property, and the ways out of that collision all involve renaming the mapped
     * property — which every `@Query` in [SpringDataMessageRepository] refers to by name. A change
     * confined to this adapter is the smaller and safer one.
     *
     * `persist` needs an open transaction; on the send path the caller supplies one through
     * `TransactionRunner`. A duplicate id no longer costs a SELECT here, which is why
     * `MessageService` keeps its explicit `existsById` guard — see the note there.
     */
    override fun save(message: Message): Message {
        val entity = MessageJpaEntity.fromDomain(message)
        entityManager.persist(entity)
        return entity.toDomain()
    }

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

    /**
     * One `persist` per row and one flush for all of them: with the merge-SELECTs gone (see [save])
     * there is nothing between the inserts, so Hibernate finally batches them at the `batch_size`
     * the configuration has been asking for all along.
     */
    override fun saveDeliveryStatuses(statuses: List<MessageDeliveryStatus>) {
        statuses.forEach { entityManager.persist(MessageDeliveryStatusJpaEntity.fromDomain(it)) }
    }

    override fun updateDeliveryStatus(messageId: UUID, userId: UUID, status: DeliveryStatus) {
        val entity = deliveryStatusRepo.findByMessageIdAndUserId(messageId, userId) ?: return
        // READ never regresses to DELIVERED. Two writers can now race for the same row: the
        // WebSocket ack the recipient's own open chat sends, and the REST DELIVERED ack #596 added
        // for a push that arrived while there was no socket to send one over. If that REST call is
        // merely slow — the FCM background service got its ten seconds, the recipient meanwhile
        // opened the chat and read it — it must not land after READ and drag the sender's tick
        // backwards to a state that is no longer true.
        if (entity.status == DeliveryStatus.READ && status != DeliveryStatus.READ) return
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
            .toCountById()
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

    override fun searchInConversation(conversationId: UUID, userId: UUID, query: String, limit: Int, offset: Int): List<Message> {
        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit)
        return messageRepo.searchInConversation(conversationId, userId, query, pageable).map { it.toDomain() }
    }

    override fun searchGlobal(userId: UUID, query: String, limit: Int, offset: Int): List<Message> {
        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit)
        return messageRepo.searchGlobal(userId, query, pageable).map { it.toDomain() }
    }

    override fun markViewOnceViewed(messageId: UUID, viewedBy: UUID, viewedAt: Instant): Int =
        messageRepo.markViewOnceViewed(messageId, viewedBy, viewedAt)

    override fun findScheduledMessagesReadyToSend(now: Instant): List<Message> =
        messageRepo.findScheduledMessagesReadyToSend(now).map { it.toDomain() }

    override fun markAsDelivered(messageId: UUID) {
        messageRepo.markScheduledAsDelivered(messageId)
    }
}

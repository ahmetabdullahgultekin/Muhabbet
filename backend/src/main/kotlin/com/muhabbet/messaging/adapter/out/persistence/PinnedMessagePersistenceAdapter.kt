package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.PinnedMessageId
import com.muhabbet.messaging.adapter.out.persistence.entity.PinnedMessageJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataPinnedMessageRepository
import com.muhabbet.messaging.domain.model.PinnedMessage
import com.muhabbet.messaging.domain.port.out.PinnedMessageRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PinnedMessagePersistenceAdapter(
    private val repo: SpringDataPinnedMessageRepository
) : PinnedMessageRepository {

    override fun save(pin: PinnedMessage): PinnedMessage =
        repo.save(PinnedMessageJpaEntity.fromDomain(pin)).toDomain()

    override fun find(conversationId: UUID, messageId: UUID): PinnedMessage? =
        repo.findById(PinnedMessageId(conversationId, messageId)).orElse(null)?.toDomain()

    override fun delete(conversationId: UUID, messageId: UUID) =
        repo.deleteById(PinnedMessageId(conversationId, messageId))

    override fun findByConversationId(conversationId: UUID): List<PinnedMessage> =
        repo.findByConversationIdOrderByPinnedAtDesc(conversationId).map { it.toDomain() }

    override fun countByConversationId(conversationId: UUID): Long =
        repo.countByConversationId(conversationId)
}

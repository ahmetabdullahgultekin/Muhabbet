package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageMentionJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageMentionRepository
import com.muhabbet.messaging.domain.model.Mention
import com.muhabbet.messaging.domain.port.out.MentionRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MentionPersistenceAdapter(
    private val mentionRepo: SpringDataMessageMentionRepository
) : MentionRepository {

    override fun saveAll(messageId: UUID, mentions: List<Mention>) {
        if (mentions.isEmpty()) return
        mentionRepo.saveAll(mentions.map { MessageMentionJpaEntity.fromDomain(messageId, it) })
    }

    override fun findByMessageId(messageId: UUID): List<Mention> =
        mentionRepo.findByMessageIdOrderByStartOffset(messageId).map { it.toDomain() }

    override fun findByMessageIds(messageIds: List<UUID>): Map<UUID, List<Mention>> {
        if (messageIds.isEmpty()) return emptyMap()
        return mentionRepo.findByMessageIdInOrderByStartOffset(messageIds)
            .groupBy({ it.messageId }, { it.toDomain() })
    }
}

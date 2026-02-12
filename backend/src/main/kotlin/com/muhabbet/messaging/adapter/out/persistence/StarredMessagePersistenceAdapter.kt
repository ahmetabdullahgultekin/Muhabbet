package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.StarredMessageJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataStarredMessageRepository
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class StarredMessagePersistenceAdapter(
    private val starredRepo: SpringDataStarredMessageRepository,
    private val messageRepo: SpringDataMessageRepository
) : StarredMessageRepository {

    @Transactional
    override fun star(userId: UUID, messageId: UUID) {
        if (!starredRepo.existsByUserIdAndMessageId(userId, messageId)) {
            starredRepo.save(StarredMessageJpaEntity(userId = userId, messageId = messageId))
        }
    }

    @Transactional
    override fun unstar(userId: UUID, messageId: UUID) {
        starredRepo.deleteByUserIdAndMessageId(userId, messageId)
    }

    override fun isStarred(userId: UUID, messageId: UUID): Boolean {
        return starredRepo.existsByUserIdAndMessageId(userId, messageId)
    }

    override fun getStarredMessages(userId: UUID, limit: Int, offset: Int): List<Message> {
        val starred = starredRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(offset / limit, limit))
        if (starred.isEmpty()) return emptyList()
        val messageIds = starred.map { it.messageId }
        return messageRepo.findAllById(messageIds).map { it.toDomain() }
    }

    override fun getStarredMessageIds(userId: UUID, messageIds: List<UUID>): Set<UUID> {
        return starredRepo.findStarredMessageIds(userId, messageIds).toSet()
    }
}

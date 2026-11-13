package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.StarredMessageJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataStarredMessageRepository
import com.muhabbet.messaging.domain.model.StarredCursor
import com.muhabbet.messaging.domain.model.StarredMessage
import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StarredMessagePersistenceAdapter(
    private val starredRepo: SpringDataStarredMessageRepository
) : StarredMessageRepository {

    override fun star(userId: UUID, messageId: UUID) {
        starredRepo.insertIfAbsent(userId, messageId)
    }

    override fun unstar(userId: UUID, messageId: UUID) {
        starredRepo.deleteByUserIdAndMessageId(userId, messageId)
    }

    override fun findVisibleStarred(userId: UUID, before: StarredCursor?, limit: Int): List<StarredMessage> {
        val page = PageRequest.ofSize(limit)
        val rows = if (before == null) {
            starredRepo.findVisibleLatest(userId, page)
        } else {
            starredRepo.findVisibleBefore(userId, before.starredAt, before.starId, page)
        }
        return rows.map { row ->
            val star = row[0] as StarredMessageJpaEntity
            val message = row[1] as MessageJpaEntity
            StarredMessage(starId = star.id, starredAt = star.createdAt, message = message.toDomain())
        }
    }
}

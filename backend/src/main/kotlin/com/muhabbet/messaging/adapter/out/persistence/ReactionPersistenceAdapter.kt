package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageReactionJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataReactionRepository
import com.muhabbet.messaging.domain.model.Reaction
import com.muhabbet.messaging.domain.port.out.ReactionRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ReactionPersistenceAdapter(
    private val reactionRepo: SpringDataReactionRepository
) : ReactionRepository {

    override fun save(reaction: Reaction): Reaction =
        reactionRepo.save(MessageReactionJpaEntity.fromDomain(reaction)).toDomain()

    override fun findByMessageId(messageId: UUID): List<Reaction> =
        reactionRepo.findByMessageId(messageId).map { it.toDomain() }

    override fun findByMessageIdAndUserIdAndEmoji(messageId: UUID, userId: UUID, emoji: String): Reaction? =
        reactionRepo.findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji)?.toDomain()

    override fun deleteByMessageIdAndUserIdAndEmoji(messageId: UUID, userId: UUID, emoji: String) {
        reactionRepo.deleteByMessageIdAndUserIdAndEmoji(messageId, userId, emoji)
    }
}

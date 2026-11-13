package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataReactionRepository
import com.muhabbet.messaging.domain.model.Reaction
import com.muhabbet.messaging.domain.port.out.ReactionRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ReactionPersistenceAdapter(
    private val reactionRepo: SpringDataReactionRepository
) : ReactionRepository {

    override fun addIfAbsent(reaction: Reaction): Boolean =
        reactionRepo.insertIfAbsent(
            id = reaction.id,
            messageId = reaction.messageId,
            userId = reaction.userId,
            emoji = reaction.emoji,
            createdAt = reaction.createdAt
        ) > 0

    override fun findByMessageId(messageId: UUID): List<Reaction> =
        reactionRepo.findByMessageId(messageId).map { it.toDomain() }

    override fun findByMessageIds(messageIds: Collection<UUID>): List<Reaction> =
        if (messageIds.isEmpty()) emptyList() else reactionRepo.findByMessageIdIn(messageIds).map { it.toDomain() }

    override fun deleteByMessageIdAndUserIdAndEmoji(messageId: UUID, userId: UUID, emoji: String) {
        reactionRepo.deleteByMessageIdAndUserIdAndEmoji(messageId, userId, emoji)
    }
}

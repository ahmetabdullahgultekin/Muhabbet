package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Reaction
import com.muhabbet.messaging.domain.port.`in`.ManageReactionUseCase
import com.muhabbet.messaging.domain.port.`in`.ReactionGroup
import com.muhabbet.messaging.domain.port.out.ReactionRepository
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class ReactionService(
    private val reactionRepository: ReactionRepository
) : ManageReactionUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun addReaction(messageId: UUID, userId: UUID, emoji: String) {
        val existing = reactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji)
        if (existing != null) {
            return
        }
        reactionRepository.save(
            Reaction(
                messageId = messageId,
                userId = userId,
                emoji = emoji
            )
        )
        log.debug("Reaction added: msg={}, user={}, emoji={}", messageId, userId, emoji)
    }

    @Transactional
    override fun removeReaction(messageId: UUID, userId: UUID, emoji: String) {
        reactionRepository.deleteByMessageIdAndUserIdAndEmoji(messageId, userId, emoji)
        log.debug("Reaction removed: msg={}, user={}, emoji={}", messageId, userId, emoji)
    }

    @Transactional(readOnly = true)
    override fun getReactions(messageId: UUID): List<ReactionGroup> {
        val reactions = reactionRepository.findByMessageId(messageId)
        return reactions.groupBy { it.emoji }
            .map { (emoji, list) ->
                ReactionGroup(
                    emoji = emoji,
                    count = list.size,
                    userIds = list.map { it.userId }
                )
            }
    }
}

package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Reaction
import com.muhabbet.messaging.domain.port.`in`.ManageReactionUseCase
import com.muhabbet.messaging.domain.port.`in`.ReactionGroup
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReactionRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Reacting writes into somebody else's conversation and the controller broadcasts the result live
 * to every member of it, so this service is a write path into a private chat that takes nothing but
 * a message id. Until #557 it checked neither who the caller was nor what they were sending.
 *
 * Both checks live here rather than in the controller: the controller already holds
 * [MessageRepository] and [ConversationRepository] for its broadcast, and putting the guard there
 * would have left the use case unguarded for any future caller.
 */
open class ReactionService(
    private val reactionRepository: ReactionRepository,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository
) : ManageReactionUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun addReaction(messageId: UUID, userId: UUID, emoji: String) {
        requireAllowedReaction(emoji)
        requireMemberOfMessageConversation(messageId, userId)

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
        requireAllowedReaction(emoji)
        requireMemberOfMessageConversation(messageId, userId)

        reactionRepository.deleteByMessageIdAndUserIdAndEmoji(messageId, userId, emoji)
        log.debug("Reaction removed: msg={}, user={}, emoji={}", messageId, userId, emoji)
    }

    /**
     * Reading is guarded too: the reply names every user who reacted, so without the check an
     * outsider holding a message id learns who is in the conversation and what they thought of it.
     */
    @Transactional(readOnly = true)
    override fun getReactions(messageId: UUID, userId: UUID): List<ReactionGroup> {
        requireMemberOfMessageConversation(messageId, userId)

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

    /**
     * Validated before the membership lookup on purpose: a rejected payload should cost no queries,
     * and the answer is the same whether or not the caller is a member, so ordering it first leaks
     * nothing.
     */
    private fun requireAllowedReaction(emoji: String) {
        if (!ValidationRules.isValidReaction(emoji)) {
            throw BusinessException(ErrorCode.MSG_INVALID_REACTION)
        }
    }

    private fun requireMemberOfMessageConversation(messageId: UUID, userId: UUID) {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)
        conversationRepository.findMember(message.conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
    }
}

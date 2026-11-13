package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.ManageStarredMessagesUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import java.util.UUID

/**
 * Stars are bookmarks on messages the user can already read, and are filtered again on the way
 * out: leaving a conversation takes its messages with it, starred ones included, and a scheduled
 * message stays its sender's until it is released.
 *
 * A message the caller cannot read answers [ErrorCode.MSG_NOT_FOUND], the same as one that does
 * not exist, so the endpoint does not confirm which message ids are real.
 */
open class StarredMessageService(
    private val starredMessageRepository: StarredMessageRepository,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository
) : ManageStarredMessagesUseCase {

    companion object {
        const val MAX_PAGE_SIZE = 100
    }

    override fun star(userId: UUID, messageId: UUID) {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)
        val member = conversationRepository.findMember(message.conversationId, userId)
        if (member == null || !isReadableBy(message, userId)) {
            throw BusinessException(ErrorCode.MSG_NOT_FOUND)
        }
        starredMessageRepository.star(userId, messageId)
    }

    override fun unstar(userId: UUID, messageId: UUID) {
        starredMessageRepository.unstar(userId, messageId)
    }

    override fun getStarredMessages(userId: UUID, limit: Int, offset: Int): List<Message> {
        val page = starredMessageRepository.getStarredMessages(
            userId,
            limit.coerceIn(1, MAX_PAGE_SIZE),
            offset.coerceAtLeast(0)
        )
        if (page.isEmpty()) return emptyList()
        val memberOf = conversationRepository.findConversationsByUserId(userId).map { it.id }.toSet()
        return page.filter { it.conversationId in memberOf && isReadableBy(it, userId) }
    }

    private fun isReadableBy(message: Message, userId: UUID): Boolean =
        !message.isScheduled || message.senderId == userId
}

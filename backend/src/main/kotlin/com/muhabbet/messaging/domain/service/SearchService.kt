package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.SearchMessagesUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import java.util.UUID

/**
 * Enforces conversation-membership authorization for message search (Phase 0 / P0-1).
 *
 * The underlying repository queries are already membership-scoped (JOIN on conversation_members
 * filtered by the requesting userId), so global search can never leak foreign messages. For a
 * targeted in-conversation search we additionally fail fast with an explicit 403 when the
 * requester is not a member, so the client gets a clear authorization error rather than an
 * empty result set.
 */
open class SearchService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository
) : SearchMessagesUseCase {

    override fun searchGlobal(requestingUserId: UUID, query: String, limit: Int, offset: Int): List<Message> =
        messageRepository.searchGlobal(requestingUserId, query, limit, offset)

    override fun searchInConversation(
        conversationId: UUID,
        requestingUserId: UUID,
        query: String,
        limit: Int,
        offset: Int
    ): List<Message> {
        conversationRepository.findMember(conversationId, requestingUserId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
        return messageRepository.searchInConversation(conversationId, requestingUserId, query, limit, offset)
    }
}

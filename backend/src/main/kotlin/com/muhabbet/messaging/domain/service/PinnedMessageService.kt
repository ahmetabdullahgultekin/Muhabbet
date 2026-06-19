package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.PinnedMessage
import com.muhabbet.messaging.domain.port.`in`.ManagePinnedMessageUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PinnedMessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class PinnedMessageService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val pinnedMessageRepository: PinnedMessageRepository
) : ManagePinnedMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_PINS_PER_CONVERSATION = 3
    }

    @Transactional
    override fun pin(conversationId: UUID, messageId: UUID, userId: UUID): PinnedMessage {
        requireMember(conversationId, userId)

        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)
        // The message must belong to this conversation — don't leak cross-conversation existence.
        if (message.conversationId != conversationId) {
            throw BusinessException(ErrorCode.MSG_NOT_FOUND)
        }

        // Idempotent: re-pinning an already-pinned message is a no-op that returns the existing pin.
        pinnedMessageRepository.find(conversationId, messageId)?.let { return it }

        if (pinnedMessageRepository.countByConversationId(conversationId) >= MAX_PINS_PER_CONVERSATION) {
            throw BusinessException(ErrorCode.MSG_PIN_LIMIT_REACHED)
        }

        val saved = pinnedMessageRepository.save(
            PinnedMessage(conversationId = conversationId, messageId = messageId, pinnedBy = userId)
        )
        log.info("Message pinned: conv={}, msg={}, by={}", conversationId, messageId, userId)
        return saved
    }

    @Transactional
    override fun unpin(conversationId: UUID, messageId: UUID, userId: UUID) {
        requireMember(conversationId, userId)
        pinnedMessageRepository.delete(conversationId, messageId)
        log.info("Message unpinned: conv={}, msg={}, by={}", conversationId, messageId, userId)
    }

    @Transactional(readOnly = true)
    override fun getPinned(conversationId: UUID, userId: UUID): List<PinnedMessage> {
        requireMember(conversationId, userId)
        return pinnedMessageRepository.findByConversationId(conversationId)
    }

    private fun requireMember(conversationId: UUID, userId: UUID) {
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
    }
}

package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.PinnedMessage
import com.muhabbet.messaging.domain.port.`in`.ManagePinnedMessageUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PinnedMessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.util.UUID

class PinnedMessageService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val pinnedMessageRepository: PinnedMessageRepository,
    private val transactions: TransactionRunner
) : ManagePinnedMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_PINS_PER_CONVERSATION = 3
    }

    override fun pin(conversationId: UUID, messageId: UUID, userId: UUID): PinnedMessage = transactions.inTransaction {
        requireMember(conversationId, userId)

        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)
        // The message must belong to this conversation — don't leak cross-conversation existence.
        if (message.conversationId != conversationId) {
            throw BusinessException(ErrorCode.MSG_NOT_FOUND)
        }

        // Idempotent: re-pinning an already-pinned message is a no-op that returns the existing pin.
        pinnedMessageRepository.find(conversationId, messageId)?.let { return@inTransaction it }

        if (pinnedMessageRepository.countByConversationId(conversationId) >= MAX_PINS_PER_CONVERSATION) {
            throw BusinessException(ErrorCode.MSG_PIN_LIMIT_REACHED)
        }

        val saved = pinnedMessageRepository.save(
            PinnedMessage(conversationId = conversationId, messageId = messageId, pinnedBy = userId)
        )
        log.info("Message pinned: conv={}, msg={}, by={}", conversationId, messageId, userId)
        return@inTransaction saved
    }

    override fun unpin(conversationId: UUID, messageId: UUID, userId: UUID) {
        transactions.inTransaction<Unit> {
            requireMember(conversationId, userId)
            pinnedMessageRepository.delete(conversationId, messageId)
            log.info("Message unpinned: conv={}, msg={}, by={}", conversationId, messageId, userId)
        }
    }

    override fun getPinned(conversationId: UUID, userId: UUID): List<PinnedMessage> = transactions.inReadOnlyTransaction {
        requireMember(conversationId, userId)
        return@inReadOnlyTransaction pinnedMessageRepository.findByConversationId(conversationId)
    }

    private fun requireMember(conversationId: UUID, userId: UUID) {
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
    }
}

package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.`in`.ManageDisappearingMessageUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class DisappearingMessageService(
    private val conversationRepository: ConversationRepository
) : ManageDisappearingMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun setDisappearTimer(conversationId: UUID, userId: UUID, seconds: Int?) {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.CONV_NOT_FOUND)

        val updated = conversation.copy(
            disappearAfterSeconds = seconds,
            updatedAt = Instant.now()
        )
        conversationRepository.updateConversation(updated)
        log.info("Disappear timer set: conv={}, seconds={}, by={}", conversationId, seconds, userId)
    }
}

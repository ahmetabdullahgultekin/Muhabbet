package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.MessagePage
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class MessageService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val messageBroadcaster: MessageBroadcaster
) : SendMessageUseCase, GetMessageHistoryUseCase, UpdateDeliveryStatusUseCase, ManageMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val EDIT_WINDOW_MINUTES = 15L
    }

    @Transactional
    override fun sendMessage(command: SendMessageCommand): Message {
        // Validate content
        if (command.contentType == ContentType.TEXT) {
            if (!ValidationRules.isValidMessageContent(command.content)) {
                if (command.content.isBlank()) throw BusinessException(ErrorCode.MSG_EMPTY_CONTENT)
                throw BusinessException(ErrorCode.MSG_CONTENT_TOO_LONG)
            }
        }

        // Verify sender is member
        val member = conversationRepository.findMember(command.conversationId, command.senderId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        // Idempotency check
        if (messageRepository.existsById(command.messageId)) {
            throw BusinessException(ErrorCode.MSG_DUPLICATE)
        }

        // Calculate expiresAt for disappearing messages
        val conversation = conversationRepository.findById(command.conversationId)
        val now = Instant.now()
        val expiresAt = conversation?.disappearAfterSeconds?.let {
            now.plusSeconds(it.toLong())
        }

        val message = messageRepository.save(
            Message(
                id = command.messageId,
                conversationId = command.conversationId,
                senderId = command.senderId,
                contentType = command.contentType,
                content = command.content,
                replyToId = command.replyToId,
                mediaUrl = command.mediaUrl,
                thumbnailUrl = command.thumbnailUrl,
                serverTimestamp = now,
                clientTimestamp = command.clientTimestamp,
                expiresAt = expiresAt,
                forwardedFrom = command.forwardedFrom
            )
        )

        // Create delivery status for all recipients
        val members = conversationRepository.findMembersByConversationId(command.conversationId)
        val recipientIds = members.map { it.userId }.filter { it != command.senderId }

        recipientIds.forEach { recipientId ->
            messageRepository.saveDeliveryStatus(
                MessageDeliveryStatus(
                    messageId = message.id,
                    userId = recipientId,
                    status = DeliveryStatus.SENT
                )
            )
        }

        // Broadcast to online recipients
        messageBroadcaster.broadcastMessage(message, recipientIds)

        log.info("Message sent: id={}, conv={}, sender={}", message.id, command.conversationId, command.senderId)
        return message
    }

    @Transactional(readOnly = true)
    override fun getMessages(
        conversationId: UUID,
        userId: UUID,
        cursor: String?,
        limit: Int,
        direction: String
    ): MessagePage {
        // Verify membership
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        val before = cursor?.let {
            try {
                Instant.parse(it)
            } catch (e: Exception) {
                null
            }
        }

        val effectiveLimit = limit.coerceIn(1, 100)
        val messages = messageRepository.findByConversationId(conversationId, before, effectiveLimit + 1)

        val hasMore = messages.size > effectiveLimit
        val page = if (hasMore) messages.take(effectiveLimit) else messages
        val nextCursor = if (hasMore) page.lastOrNull()?.serverTimestamp?.toString() else null

        return MessagePage(items = page, nextCursor = nextCursor, hasMore = hasMore)
    }

    @Transactional
    override fun updateStatus(messageId: UUID, userId: UUID, status: DeliveryStatus) {
        messageRepository.updateDeliveryStatus(messageId, userId, status)

        val message = messageRepository.findById(messageId) ?: return
        messageBroadcaster.broadcastStatusUpdate(messageId, message.conversationId, userId, message.senderId, status)

        log.debug("Delivery status updated: msg={}, user={}, status={}", messageId, userId, status)
    }

    @Transactional
    override fun markConversationRead(conversationId: UUID, userId: UUID) {
        messageRepository.markConversationRead(conversationId, userId)
        conversationRepository.updateLastReadAt(conversationId, userId, Instant.now())
        log.debug("Conversation marked as read: conv={}, user={}", conversationId, userId)
    }

    // ─── Message Management ──────────────────────────────────

    @Transactional
    override fun deleteMessage(messageId: UUID, requesterId: UUID) {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        if (message.senderId != requesterId) {
            throw BusinessException(ErrorCode.MSG_NOT_SENDER)
        }
        if (message.isDeleted) {
            throw BusinessException(ErrorCode.MSG_ALREADY_DELETED)
        }

        messageRepository.softDelete(messageId)

        val members = conversationRepository.findMembersByConversationId(message.conversationId)
        messageBroadcaster.broadcastToUsers(
            members.map { it.userId },
            WsMessage.MessageDeleted(
                messageId = messageId.toString(),
                conversationId = message.conversationId.toString(),
                deletedBy = requesterId.toString(),
                timestamp = System.currentTimeMillis()
            )
        )

        log.info("Message {} soft-deleted by {}", messageId, requesterId)
    }

    @Transactional
    override fun editMessage(messageId: UUID, requesterId: UUID, newContent: String): Message {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        if (message.senderId != requesterId) {
            throw BusinessException(ErrorCode.MSG_NOT_SENDER)
        }
        if (message.isDeleted) {
            throw BusinessException(ErrorCode.MSG_ALREADY_DELETED)
        }

        val minutesSinceSent = java.time.Duration.between(message.serverTimestamp, Instant.now()).toMinutes()
        if (minutesSinceSent > EDIT_WINDOW_MINUTES) {
            throw BusinessException(ErrorCode.MSG_EDIT_WINDOW_EXPIRED)
        }

        if (!ValidationRules.isValidMessageContent(newContent)) {
            if (newContent.isBlank()) throw BusinessException(ErrorCode.MSG_EMPTY_CONTENT)
            throw BusinessException(ErrorCode.MSG_CONTENT_TOO_LONG)
        }

        val editedAt = Instant.now()
        messageRepository.updateContent(messageId, newContent, editedAt)

        val members = conversationRepository.findMembersByConversationId(message.conversationId)
        messageBroadcaster.broadcastToUsers(
            members.map { it.userId },
            WsMessage.MessageEdited(
                messageId = messageId.toString(),
                conversationId = message.conversationId.toString(),
                editedBy = requesterId.toString(),
                newContent = newContent,
                editedAt = editedAt.toEpochMilli()
            )
        )

        log.info("Message {} edited by {}", messageId, requesterId)
        return message.copy(content = newContent, editedAt = editedAt)
    }
}

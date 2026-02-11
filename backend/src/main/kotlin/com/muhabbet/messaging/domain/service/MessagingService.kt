package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.ConversationPage
import com.muhabbet.messaging.domain.port.`in`.ConversationSummary
import com.muhabbet.messaging.domain.port.`in`.ConversationWithMembers
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.messaging.domain.port.`in`.GetConversationsUseCase
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.MessagePage
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class MessagingService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val messageBroadcaster: MessageBroadcaster,
    private val eventPublisher: ApplicationEventPublisher
) : CreateConversationUseCase, GetConversationsUseCase, SendMessageUseCase, GetMessageHistoryUseCase, UpdateDeliveryStatusUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun createConversation(
        type: ConversationType,
        creatorId: UUID,
        participantIds: List<UUID>,
        name: String?
    ): ConversationWithMembers {
        // Validate participants exist
        val allParticipantIds = (participantIds + creatorId).distinct()
        for (pid in allParticipantIds) {
            if (userRepository.findById(pid) == null) {
                throw BusinessException(ErrorCode.CONV_INVALID_PARTICIPANTS)
            }
        }

        if (type == ConversationType.DIRECT) {
            if (participantIds.size != 1) {
                throw BusinessException(ErrorCode.CONV_INVALID_PARTICIPANTS, "Direct conversations require exactly 1 other participant")
            }

            // Check for existing direct conversation
            val (low, high) = sortUuids(creatorId, participantIds[0])
            val existingConvId = conversationRepository.findDirectConversation(low, high)
            if (existingConvId != null) {
                val conv = conversationRepository.findById(existingConvId)
                    ?: throw BusinessException(ErrorCode.CONV_NOT_FOUND)
                val members = conversationRepository.findMembersByConversationId(existingConvId)
                return ConversationWithMembers(conv, members)
            }
        }

        if (type == ConversationType.GROUP) {
            if (name.isNullOrBlank()) {
                throw BusinessException(ErrorCode.VALIDATION_ERROR, "Grup adı gerekli")
            }
            if (!ValidationRules.isValidGroupName(name)) {
                throw BusinessException(ErrorCode.VALIDATION_ERROR, "Geçersiz grup adı")
            }
            if (allParticipantIds.size > ValidationRules.MAX_GROUP_MEMBERS) {
                throw BusinessException(ErrorCode.CONV_MAX_MEMBERS)
            }
        }

        val conversation = conversationRepository.save(
            Conversation(
                type = type,
                name = name,
                createdBy = creatorId
            )
        )

        // Add members
        val members = allParticipantIds.map { userId ->
            val role = if (userId == creatorId && type == ConversationType.GROUP) MemberRole.OWNER else MemberRole.MEMBER
            conversationRepository.saveMember(
                ConversationMember(
                    conversationId = conversation.id,
                    userId = userId,
                    role = role
                )
            )
        }

        // Save direct lookup
        if (type == ConversationType.DIRECT) {
            val (low, high) = sortUuids(creatorId, participantIds[0])
            conversationRepository.saveDirectLookup(low, high, conversation.id)
        }

        log.info("Conversation created: id={}, type={}, members={}", conversation.id, type, allParticipantIds.size)
        return ConversationWithMembers(conversation, members)
    }

    @Transactional(readOnly = true)
    override fun getConversations(userId: UUID, cursor: String?, limit: Int): ConversationPage {
        val conversations = conversationRepository.findConversationsByUserId(userId)

        // Build summaries with last message info
        val summaries = conversations.map { conv ->
            val lastMessage = messageRepository.getLastMessage(conv.id)
            val unreadCount = messageRepository.getUnreadCount(conv.id, userId)
            val memberIds = conversationRepository.findMembersByConversationId(conv.id)
                .map { it.userId }

            ConversationSummary(
                conversationId = conv.id,
                type = conv.type.name.lowercase(),
                name = conv.name,
                avatarUrl = conv.avatarUrl,
                lastMessagePreview = lastMessage?.content?.take(100),
                lastMessageAt = lastMessage?.serverTimestamp?.toString(),
                unreadCount = unreadCount,
                participantIds = memberIds
            )
        }
            .sortedByDescending { it.lastMessageAt ?: "" }

        // Simple cursor-based pagination
        val effectiveLimit = limit.coerceIn(1, 50)
        val startIndex = if (cursor != null) {
            summaries.indexOfFirst { it.conversationId.toString() == cursor } + 1
        } else {
            0
        }.coerceAtLeast(0)

        val page = summaries.drop(startIndex).take(effectiveLimit)
        val hasMore = startIndex + effectiveLimit < summaries.size
        val nextCursor = if (hasMore) page.lastOrNull()?.conversationId?.toString() else null

        return ConversationPage(items = page, nextCursor = nextCursor, hasMore = hasMore)
    }

    @Transactional
    override fun sendMessage(command: SendMessageCommand): Message {
        // Validate content
        if (command.contentType == com.muhabbet.messaging.domain.model.ContentType.TEXT) {
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

        val message = messageRepository.save(
            Message(
                id = command.messageId,
                conversationId = command.conversationId,
                senderId = command.senderId,
                contentType = command.contentType,
                content = command.content,
                replyToId = command.replyToId,
                mediaUrl = command.mediaUrl,
                serverTimestamp = Instant.now(),
                clientTimestamp = command.clientTimestamp
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
        messageBroadcaster.broadcastStatusUpdate(messageId, message.conversationId, userId, status)

        log.debug("Delivery status updated: msg={}, user={}, status={}", messageId, userId, status)
    }

    @Transactional
    override fun markConversationRead(conversationId: UUID, userId: UUID) {
        messageRepository.markConversationRead(conversationId, userId)
        conversationRepository.updateLastReadAt(conversationId, userId, Instant.now())
        log.debug("Conversation marked as read: conv={}, user={}", conversationId, userId)
    }

    private fun sortUuids(a: UUID, b: UUID): Pair<UUID, UUID> {
        return if (a.toString() < b.toString()) Pair(a, b) else Pair(b, a)
    }
}

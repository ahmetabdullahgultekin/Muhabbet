package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.Mention
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.MessagePage
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MentionRepository
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
    private val messageBroadcaster: MessageBroadcaster,
    private val mentionRepository: MentionRepository,
    // @mentions feature flag (muhabbet.mentions.enabled) — when false, mentions are ignored entirely
    // and the send/history path is byte-identical to pre-mentions behaviour.
    private val mentionsEnabled: Boolean = false
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

        // Check announcement mode — only admins/owners can send
        val conv = conversationRepository.findById(command.conversationId)
        if (conv != null && conv.announcementOnly && member.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.MSG_ANNOUNCEMENT_ONLY)
        }

        // Idempotency check
        if (messageRepository.existsById(command.messageId)) {
            throw BusinessException(ErrorCode.MSG_DUPLICATE)
        }

        // Resolve @mentions (Tier 2 — only when the flag is ON; otherwise stays empty/false).
        // @everyone requires the sender to be ADMIN/OWNER; individual mentions of non-members
        // are dropped (validated against current membership). See docs/design/T2-group-mentions.md.
        val members = conversationRepository.findMembersByConversationId(command.conversationId)
        val (validMentions, everyone) = resolveMentions(command, member, members)

        // Calculate expiresAt for disappearing messages
        val conversation = conversationRepository.findById(command.conversationId)
        val now = Instant.now()
        val expiresAt = conversation?.disappearAfterSeconds?.let {
            now.plusSeconds(it.toLong())
        }

        val isScheduled = command.scheduledAt != null && command.scheduledAt.isAfter(now)

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
                forwardedFrom = command.forwardedFrom,
                viewOnce = command.viewOnce,
                scheduledAt = command.scheduledAt,
                isScheduled = isScheduled,
                mentions = validMentions,
                mentionsEveryone = everyone
            )
        )

        // Persist mention rows (only ever non-empty when the flag is ON and the sender mentioned
        // current members). @everyone is carried on the message row itself, not as N mention rows.
        mentionRepository.saveAll(message.id, validMentions)

        // Scheduled messages are not delivered immediately
        if (isScheduled) {
            log.info("Scheduled message saved: id={}, conv={}, scheduledAt={}", message.id, command.conversationId, command.scheduledAt)
            return message
        }

        // Create delivery status for all recipients
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

    /**
     * Validates inbound @mentions against current membership (Tier 2). Returns the mention rows to
     * persist plus the resolved `@everyone` flag.
     *
     * - Flag OFF → always `(emptyList, false)` so the path is behaviour-neutral.
     * - `@everyone` requires the sender to be ADMIN/OWNER, else `MSG_MENTION_EVERYONE_FORBIDDEN`.
     * - Individual mentions of users who are NOT members are silently dropped (not fatal) — avoids
     *   notification-spam / membership leakage. Duplicates per user are de-duplicated.
     */
    private fun resolveMentions(
        command: SendMessageCommand,
        sender: com.muhabbet.messaging.domain.model.ConversationMember,
        members: List<com.muhabbet.messaging.domain.model.ConversationMember>
    ): Pair<List<Mention>, Boolean> {
        if (!mentionsEnabled) return emptyList<Mention>() to false

        if (command.mentionsEveryone && sender.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.MSG_MENTION_EVERYONE_FORBIDDEN)
        }

        val memberIds = members.mapTo(HashSet()) { it.userId }
        val valid = command.mentions
            .filter { it.mentionedUserId in memberIds }
            .distinctBy { it.mentionedUserId }

        return valid to command.mentionsEveryone
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

        return MessagePage(items = hydrateMentions(page), nextCursor = nextCursor, hasMore = hasMore)
    }

    /**
     * Attaches persisted @mention rows to the given messages (batch read). No-op when the flag is
     * OFF so the read path is behaviour-neutral. `mentionsEveryone` already rides on the message row.
     */
    private fun hydrateMentions(messages: List<Message>): List<Message> {
        if (!mentionsEnabled || messages.isEmpty()) return messages
        val byMessageId = mentionRepository.findByMessageIds(messages.map { it.id })
        if (byMessageId.isEmpty()) return messages
        return messages.map { msg ->
            byMessageId[msg.id]?.let { msg.copy(mentions = it) } ?: msg
        }
    }

    @Transactional
    override fun updateStatus(messageId: UUID, userId: UUID, status: DeliveryStatus) {
        val message = messageRepository.findById(messageId) ?: return

        // Authorize: only a member of the conversation may move a delivery status (and trigger the
        // resulting StatusUpdate broadcast to the sender). Without this a non-member who knew/guessed
        // a messageId could spoof a DELIVERED/READ receipt to the real sender. The underlying write is
        // already keyed by (messageId, userId) so it no-ops for non-recipients, but the broadcast was
        // unguarded — closing the read-receipt spoof.
        conversationRepository.findMember(message.conversationId, userId) ?: return

        messageRepository.updateDeliveryStatus(messageId, userId, status)
        messageBroadcaster.broadcastStatusUpdate(messageId, message.conversationId, userId, message.senderId, status)

        log.debug("Delivery status updated: msg={}, user={}, status={}", messageId, userId, status)
    }

    @Transactional
    override fun markConversationRead(conversationId: UUID, userId: UUID) {
        messageRepository.markConversationRead(conversationId, userId)
        conversationRepository.updateLastReadAt(conversationId, userId, Instant.now())
        log.debug("Conversation marked as read: conv={}, user={}", conversationId, userId)
    }

    @Transactional(readOnly = true)
    override fun resolveDeliveryStatuses(messages: List<Message>, requestingUserId: UUID): Map<UUID, DeliveryStatus> {
        if (messages.isEmpty()) return emptyMap()

        val messageIds = messages.map { it.id }
        val allStatuses = messageRepository.getDeliveryStatuses(messageIds)
        val statusesByMessageId = allStatuses.groupBy { it.messageId }

        return messages.associate { message ->
            val statuses = statusesByMessageId[message.id] ?: emptyList()
            val resolved = if (message.senderId == requestingUserId) {
                // Sender perspective: aggregate across all recipients
                // all READ → READ, any DELIVERED/READ → DELIVERED, else SENT
                if (statuses.isEmpty()) DeliveryStatus.SENT
                else if (statuses.all { it.status == DeliveryStatus.READ }) DeliveryStatus.READ
                else if (statuses.any { it.status == DeliveryStatus.DELIVERED || it.status == DeliveryStatus.READ }) DeliveryStatus.DELIVERED
                else DeliveryStatus.SENT
            } else {
                // Recipient perspective: their own status row
                statuses.firstOrNull { it.userId == requestingUserId }?.status ?: DeliveryStatus.SENT
            }
            message.id to resolved
        }
    }

    @Transactional(readOnly = true)
    override fun getMediaMessages(conversationId: UUID, userId: UUID, limit: Int, offset: Int): List<Message> {
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
        return messageRepository.findMediaByConversationId(conversationId, limit.coerceIn(1, 100), offset.coerceAtLeast(0))
    }

    @Transactional(readOnly = true)
    override fun getMessagesSince(userId: UUID, since: Instant): List<Message> {
        return messageRepository.findUndeliveredForUser(userId, since)
    }

    @Transactional(readOnly = true)
    override fun getMessageInfo(messageId: UUID, requesterId: UUID): com.muhabbet.messaging.domain.port.`in`.MessageInfo {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        // Authorize FIRST: only members of the conversation may read message info
        // (content, senderId, recipient list). Closes the getMessageInfo IDOR.
        conversationRepository.findMember(message.conversationId, requesterId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        val statuses = messageRepository.getDeliveryStatuses(listOf(messageId))
        return com.muhabbet.messaging.domain.port.`in`.MessageInfo(message = message, deliveryStatuses = statuses)
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

    // ─── View-Once ────────────────────────────────────────────

    @Transactional
    open fun markViewOnceViewed(messageId: UUID, userId: UUID) {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        // Authorize FIRST: a non-member who knows the messageId must not be able to burn it.
        // Closes the markViewOnceViewed IDOR.
        conversationRepository.findMember(message.conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        if (!message.viewOnce) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }

        if (message.viewedAt != null) {
            throw BusinessException(ErrorCode.MSG_VIEW_ONCE_ALREADY_VIEWED)
        }

        if (message.senderId == userId) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }

        messageRepository.markViewOnceViewed(messageId, userId)
        log.info("View-once message viewed: msg={}, user={}", messageId, userId)
    }

    // ─── Scheduled Messages ──────────────────────────────────

    open fun deliverScheduledMessages() {
        val now = Instant.now()
        val scheduledMessages = messageRepository.findScheduledMessagesReadyToSend(now)

        for (message in scheduledMessages) {
            messageRepository.markAsDelivered(message.id)

            val members = conversationRepository.findMembersByConversationId(message.conversationId)
            val recipientIds = members.map { it.userId }.filter { it != message.senderId }

            recipientIds.forEach { recipientId ->
                messageRepository.saveDeliveryStatus(
                    MessageDeliveryStatus(
                        messageId = message.id,
                        userId = recipientId,
                        status = DeliveryStatus.SENT
                    )
                )
            }

            messageBroadcaster.broadcastMessage(message, recipientIds)
            log.info("Scheduled message delivered: id={}, conv={}", message.id, message.conversationId)
        }
    }
}

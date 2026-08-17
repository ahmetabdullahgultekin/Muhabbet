package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.MessageInfo
import com.muhabbet.messaging.domain.port.`in`.MessagePage
import com.muhabbet.messaging.domain.port.`in`.MessageRecipient
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.`in`.ViewOnceReveal
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
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
    private val userDirectory: UserDirectoryPort,
    private val readReceiptPolicy: ReadReceiptPolicyPort,
    private val blockPolicy: BlockPolicyPort
) : SendMessageUseCase, GetMessageHistoryUseCase, UpdateDeliveryStatusUseCase, ManageMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val EDIT_WINDOW_MINUTES = 15L
    }

    /**
     * The one place a message is created, for both transports — the WebSocket handler and the REST
     * fallback call this same use case — which is why the block is enforced here and nowhere else.
     *
     * **The blocked sender is not told.** A dropped message is answered with the same
     * `ServerAck(OK)` / `200` a delivered one gets, so the sender's client shows a single tick that
     * never becomes two. That is what every messenger does, and it is a decision rather than an
     * accident of where the check sits: the alternative — an error the client can see — turns a
     * block into a probe, telling a harasser exactly who has blocked them and when they unblock.
     */
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

        // One lookup, three uses: announcement mode, the disappearing-message window, and the
        // direct-conversation test the block check needs. It used to be fetched twice.
        val conversation = conversationRepository.findById(command.conversationId)

        // Check announcement mode — only admins/owners can send
        if (conversation != null && conversation.announcementOnly &&
            member.role == com.muhabbet.messaging.domain.model.MemberRole.MEMBER
        ) {
            throw BusinessException(ErrorCode.MSG_ANNOUNCEMENT_ONLY)
        }

        // Idempotency check
        if (messageRepository.existsById(command.messageId)) {
            throw BusinessException(ErrorCode.MSG_DUPLICATE)
        }

        // Calculate expiresAt for disappearing messages
        val now = Instant.now()
        val expiresAt = conversation?.disappearAfterSeconds?.let {
            now.plusSeconds(it.toLong())
        }

        val isScheduled = command.scheduledAt != null && command.scheduledAt.isAfter(now)

        val draft = Message(
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
            isScheduled = isScheduled
        )

        // Dropped *before* the insert, deliberately. Persisting it and filtering on the way out
        // would need the same filter on history, background sync, search, shared media and the
        // push fan-out — five chances to leak, and a leak means the block does not work. With no
        // row there is nothing for any of them to return.
        if (isBlockedDirectSend(conversation, command.senderId)) {
            log.info(
                "Message dropped, recipient has blocked the sender: conv={}, sender={}",
                command.conversationId,
                command.senderId
            )
            return draft
        }

        val message = messageRepository.save(draft)

        // Scheduled messages are not delivered immediately
        if (isScheduled) {
            log.info("Scheduled message saved: id={}, conv={}, scheduledAt={}", message.id, command.conversationId, command.scheduledAt)
            return message
        }

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

    /**
     * Whether this send is a direct message to someone who has blocked the sender.
     *
     * **Direct conversations only, and deliberately so.** In a group a block does not stop the
     * message — WhatsApp behaves the same way — because a group message has one sender and many
     * readers, and dropping it because one of thirty members has blocked the sender would silently
     * remove it for the other twenty-nine. What a block owes you in a group is the ability to
     * leave it, not the power to censor a room.
     *
     * **One direction only.** The question is whether the *recipient* blocked the *sender*. Someone
     * messaging a person they themselves blocked is not the case with a victim, and swallowing
     * their own outgoing message would read as a bug rather than a policy.
     */
    private fun isBlockedDirectSend(conversation: Conversation?, senderId: UUID): Boolean {
        if (conversation?.type != ConversationType.DIRECT) return false
        val recipientId = conversationRepository.findMembersByConversationId(conversation.id)
            .map { it.userId }
            .firstOrNull { it != senderId }
            ?: return false
        return blockPolicy.hasBlocked(recipientId, senderId)
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
        // A reader who has turned read receipts off still gets their own row stored as READ — that
        // row is what clears their unread badge — but the sender must not be told. We downgrade what
        // is *published*, never what is stored, because one column serves both concerns.
        // Only READ can be downgraded, so DELIVERED acks skip the lookup entirely — this runs on
        // every ack from every client.
        val published = if (status == DeliveryStatus.READ) {
            publishableStatus(userId, status, readReceiptPolicy.findReadReceiptsDisabled(listOf(userId)))
        } else {
            status
        }
        messageBroadcaster.broadcastStatusUpdate(messageId, message.conversationId, userId, message.senderId, published)

        log.debug("Delivery status updated: msg={}, user={}, stored={}, published={}", messageId, userId, status, published)
    }

    /**
     * READ from a reader who has receipts off is published as DELIVERED — the sender learns the
     * message arrived, never that it was opened. Every other status passes through untouched.
     */
    private fun publishableStatus(
        readerId: UUID,
        status: DeliveryStatus,
        receiptsDisabled: Set<UUID>
    ): DeliveryStatus =
        if (status == DeliveryStatus.READ && readerId in receiptsDisabled) DeliveryStatus.DELIVERED else status

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
        // One batched lookup for the whole page, and only for rows that are actually READ — the
        // port short-circuits on an empty collection, so a page nobody has read costs no query.
        // Without this the live WS downgrade in [updateStatus] would be undone the moment the
        // sender scrolled or reopened the chat, because the stored row is still READ.
        val receiptsDisabled = readReceiptPolicy.findReadReceiptsDisabled(
            allStatuses.filter { it.status == DeliveryStatus.READ }.map { it.userId }
        )

        return messages.associate { message ->
            val statuses = statusesByMessageId[message.id] ?: emptyList()
            val resolved = if (message.senderId == requestingUserId) {
                // Sender perspective: aggregate across all recipients
                // all READ → READ, any DELIVERED/READ → DELIVERED, else SENT
                val visible = statuses.map { publishableStatus(it.userId, it.status, receiptsDisabled) }
                if (visible.isEmpty()) DeliveryStatus.SENT
                else if (visible.all { it == DeliveryStatus.READ }) DeliveryStatus.READ
                else if (visible.any { it == DeliveryStatus.DELIVERED || it == DeliveryStatus.READ }) DeliveryStatus.DELIVERED
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
    override fun getMessageInfo(messageId: UUID, requesterId: UUID): MessageInfo {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        // Authorize FIRST: only members of the conversation may read message info
        // (content, senderId, recipient list). Closes the getMessageInfo IDOR.
        conversationRepository.findMember(message.conversationId, requesterId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        // The sender is not a recipient of their own message.
        val statuses = messageRepository.getDeliveryStatuses(listOf(messageId))
            .filter { it.userId != message.senderId }

        // One batched lookup for the whole recipient list — resolving names one at a time was an N+1.
        val displayInfo = userDirectory.findDisplayInfo(statuses.map { it.userId })

        return MessageInfo(
            message = message,
            recipients = statuses.map { status ->
                val user = displayInfo[status.userId]
                MessageRecipient(
                    userId = status.userId,
                    displayName = user?.displayName,
                    avatarUrl = user?.avatarUrl,
                    status = status.status,
                    updatedAt = status.updatedAt
                )
            }
        )
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

    /**
     * Burns a view-once message and hands back its media, once.
     *
     * The order matters and every step earns its place:
     *
     * - **Authorize before anything else** — a non-member who knows the messageId must not be able
     *   to burn someone else's message (the IDOR closed by #55).
     * - **The sender is refused**, so opening your own view-once photo cannot consume the
     *   recipient's one look. The sender's own copy is stripped by `toSharedMessage` too; a
     *   view-once photo is not re-viewable by anyone once it leaves the composer.
     * - **The write is the arbiter, not the read.** `viewedAt != null` above is a cheap rejection
     *   for the common case, but two taps can both pass it. The conditional UPDATE can only match
     *   once, so a zero row count means someone else won and this caller gets nothing.
     */
    @Transactional
    override fun markViewOnceViewed(messageId: UUID, userId: UUID): ViewOnceReveal {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

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

        val viewedAt = Instant.now()
        if (messageRepository.markViewOnceViewed(messageId, userId, viewedAt) == 0) {
            throw BusinessException(ErrorCode.MSG_VIEW_ONCE_ALREADY_VIEWED)
        }

        log.info("View-once message viewed: msg={}, user={}", messageId, userId)
        return ViewOnceReveal(
            messageId = messageId,
            mediaUrl = message.mediaUrl,
            thumbnailUrl = message.thumbnailUrl,
            viewedAt = viewedAt
        )
    }

    // ─── Scheduled Messages ──────────────────────────────────

    open fun deliverScheduledMessages() {
        val now = Instant.now()
        val scheduledMessages = messageRepository.findScheduledMessagesReadyToSend(now)

        for (message in scheduledMessages) {
            messageRepository.markAsDelivered(message.id)

            // A message can be scheduled before a block and come due after it. The row already
            // exists — it was written when the send was scheduled — so this cannot un-write it,
            // but it stops the delivery rows, the socket fan-out and the push notification.
            val conversation = conversationRepository.findById(message.conversationId)
            if (isBlockedDirectSend(conversation, message.senderId)) {
                log.info(
                    "Scheduled message not delivered, recipient has blocked the sender: id={}, conv={}",
                    message.id,
                    message.conversationId
                )
                continue
            }

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

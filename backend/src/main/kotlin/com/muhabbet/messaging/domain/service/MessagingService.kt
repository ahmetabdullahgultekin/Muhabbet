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
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
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
import com.muhabbet.shared.protocol.GroupMemberInfo
import com.muhabbet.shared.protocol.WsMessage
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
) : CreateConversationUseCase, GetConversationsUseCase, SendMessageUseCase, GetMessageHistoryUseCase,
    UpdateDeliveryStatusUseCase, ManageGroupUseCase, ManageMessageUseCase {

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
                participantIds = memberIds,
                disappearAfterSeconds = conv.disappearAfterSeconds
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
                expiresAt = expiresAt
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

    // ─── Group Management ──────────────────────────────────

    @Transactional
    override fun addMembers(conversationId: UUID, requesterId: UUID, userIds: List<UUID>): List<ConversationMember> {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT)
        }

        requireAdminOrOwner(conversationId, requesterId)

        val existingMembers = conversationRepository.findMembersByConversationId(conversationId)
        val existingUserIds = existingMembers.map { it.userId }.toSet()

        val newUserIds = userIds.filter { it !in existingUserIds }
        if (newUserIds.isEmpty()) throw BusinessException(ErrorCode.GROUP_ALREADY_MEMBER)

        if (existingMembers.size + newUserIds.size > ValidationRules.MAX_GROUP_MEMBERS) {
            throw BusinessException(ErrorCode.CONV_MAX_MEMBERS)
        }

        // Validate all users exist
        newUserIds.forEach { uid ->
            userRepository.findById(uid) ?: throw BusinessException(ErrorCode.CONV_INVALID_PARTICIPANTS)
        }

        val addedMembers = newUserIds.map { uid ->
            conversationRepository.saveMember(
                ConversationMember(conversationId = conversationId, userId = uid, role = MemberRole.MEMBER)
            )
        }

        // Broadcast to all group members
        val allMemberIds = (existingUserIds + newUserIds).toList()
        val memberInfos = addedMembers.map { m ->
            val user = userRepository.findById(m.userId)
            GroupMemberInfo(userId = m.userId.toString(), displayName = user?.displayName, role = m.role.name)
        }
        messageBroadcaster.broadcastToUsers(
            allMemberIds,
            WsMessage.GroupMemberAdded(
                conversationId = conversationId.toString(),
                addedBy = requesterId.toString(),
                members = memberInfos
            )
        )

        log.info("Members added to group {}: {}", conversationId, newUserIds)
        return addedMembers
    }

    @Transactional
    override fun removeMember(conversationId: UUID, requesterId: UUID, targetUserId: UUID) {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT)
        }

        val targetMember = conversationRepository.findMember(conversationId, targetUserId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)

        if (targetMember.role == MemberRole.OWNER) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_REMOVE_OWNER)
        }

        val requesterMember = requireMember(conversationId, requesterId)
        // ADMIN can remove MEMBER, OWNER can remove anyone except OWNER
        if (requesterMember.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
        if (requesterMember.role == MemberRole.ADMIN && targetMember.role == MemberRole.ADMIN) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }

        conversationRepository.removeMember(conversationId, targetUserId)

        // Broadcast to remaining members + removed user
        val members = conversationRepository.findMembersByConversationId(conversationId)
        val allIds = members.map { it.userId } + targetUserId
        messageBroadcaster.broadcastToUsers(
            allIds,
            WsMessage.GroupMemberRemoved(
                conversationId = conversationId.toString(),
                removedBy = requesterId.toString(),
                userId = targetUserId.toString()
            )
        )

        log.info("Member {} removed from group {} by {}", targetUserId, conversationId, requesterId)
    }

    @Transactional
    override fun updateGroupInfo(conversationId: UUID, requesterId: UUID, name: String?, description: String?): Conversation {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT)
        }

        requireAdminOrOwner(conversationId, requesterId)

        if (name != null && !ValidationRules.isValidGroupName(name)) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR, "Geçersiz grup adı")
        }

        val updated = conversation.copy(
            name = name ?: conversation.name,
            description = description ?: conversation.description,
            updatedAt = Instant.now()
        )
        val saved = conversationRepository.updateConversation(updated)

        // Broadcast
        val memberIds = conversationRepository.findMembersByConversationId(conversationId).map { it.userId }
        messageBroadcaster.broadcastToUsers(
            memberIds,
            WsMessage.GroupInfoUpdated(
                conversationId = conversationId.toString(),
                updatedBy = requesterId.toString(),
                name = name,
                description = description
            )
        )

        log.info("Group info updated: {}", conversationId)
        return saved
    }

    @Transactional
    override fun updateMemberRole(conversationId: UUID, requesterId: UUID, targetUserId: UUID, newRole: MemberRole) {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT)
        }

        val requesterMember = requireMember(conversationId, requesterId)
        if (requesterMember.role != MemberRole.OWNER) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED, "Sadece grup sahibi rol değiştirebilir")
        }

        conversationRepository.findMember(conversationId, targetUserId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)

        conversationRepository.updateMemberRole(conversationId, targetUserId, newRole)

        val memberIds = conversationRepository.findMembersByConversationId(conversationId).map { it.userId }
        messageBroadcaster.broadcastToUsers(
            memberIds,
            WsMessage.GroupRoleUpdated(
                conversationId = conversationId.toString(),
                updatedBy = requesterId.toString(),
                userId = targetUserId.toString(),
                newRole = newRole.name
            )
        )

        log.info("Member {} role updated to {} in group {} by {}", targetUserId, newRole, conversationId, requesterId)
    }

    @Transactional
    override fun leaveGroup(conversationId: UUID, userId: UUID) {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT)
        }

        val member = requireMember(conversationId, userId)

        if (member.role == MemberRole.OWNER) {
            // Transfer ownership to oldest admin, then oldest member
            val members = conversationRepository.findMembersByConversationId(conversationId)
                .filter { it.userId != userId }
                .sortedBy { it.joinedAt }

            val newOwner = members.firstOrNull { it.role == MemberRole.ADMIN }
                ?: members.firstOrNull()

            if (newOwner == null) {
                // Last member — just remove
                conversationRepository.removeMember(conversationId, userId)
                log.info("Last member {} left group {}, group is empty", userId, conversationId)
                return
            }

            conversationRepository.updateMemberRole(conversationId, newOwner.userId, MemberRole.OWNER)
            log.info("Ownership transferred to {} in group {}", newOwner.userId, conversationId)
        }

        conversationRepository.removeMember(conversationId, userId)

        val remainingMembers = conversationRepository.findMembersByConversationId(conversationId)
        messageBroadcaster.broadcastToUsers(
            remainingMembers.map { it.userId },
            WsMessage.GroupMemberLeft(conversationId = conversationId.toString(), userId = userId.toString())
        )

        log.info("User {} left group {}", userId, conversationId)
    }

    // ─── Message Management ──────────────────────────────────

    companion object {
        private const val EDIT_WINDOW_MINUTES = 15L
    }

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

    // ─── Helpers ──────────────────────────────────────────────

    private fun requireMember(conversationId: UUID, userId: UUID): ConversationMember {
        return conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)
    }

    private fun requireAdminOrOwner(conversationId: UUID, userId: UUID): ConversationMember {
        val member = requireMember(conversationId, userId)
        if (member.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
        return member
    }

    private fun sortUuids(a: UUID, b: UUID): Pair<UUID, UUID> {
        return if (a.toString() < b.toString()) Pair(a, b) else Pair(b, a)
    }
}

package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.ConversationPage
import com.muhabbet.messaging.domain.port.`in`.ConversationSummary
import com.muhabbet.messaging.domain.port.`in`.ConversationWithMembers
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.messaging.domain.port.`in`.GetConversationsUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class ConversationService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) : CreateConversationUseCase, GetConversationsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun createConversation(
        type: ConversationType,
        creatorId: UUID,
        participantIds: List<UUID>,
        name: String?
    ): ConversationWithMembers {
        // Early validation for direct conversations
        if (type == ConversationType.DIRECT) {
            if (participantIds.size != 1) {
                throw BusinessException(ErrorCode.CONV_INVALID_PARTICIPANTS, "Direct conversations require exactly 1 other participant")
            }
            if (participantIds[0] == creatorId) {
                throw BusinessException(ErrorCode.CONV_INVALID_PARTICIPANTS, "Cannot create conversation with yourself")
            }
        }

        // Validate participants exist (batch query instead of N individual lookups)
        val allParticipantIds = (participantIds + creatorId).distinct()
        val existingUsers = userRepository.findAllByIds(allParticipantIds)
        if (existingUsers.size != allParticipantIds.size) {
            throw BusinessException(ErrorCode.CONV_INVALID_PARTICIPANTS)
        }

        if (type == ConversationType.DIRECT) {

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
        if (conversations.isEmpty()) {
            return ConversationPage(items = emptyList(), nextCursor = null, hasMore = false)
        }

        val conversationIds = conversations.map { it.id }

        // Batch queries: 3 queries instead of 3*N (critical N+1 fix)
        val lastMessageMap = messageRepository.getLastMessages(conversationIds)
        val unreadCountMap = messageRepository.getUnreadCounts(conversationIds, userId)
        val membersMap = conversationRepository.findMembersByConversationIds(conversationIds)

        val summaries = conversations.map { conv ->
            val lastMessage = lastMessageMap[conv.id]
            val members = membersMap[conv.id] ?: emptyList()
            val myMember = members.firstOrNull { it.userId == userId }

            ConversationSummary(
                conversationId = conv.id,
                type = conv.type.name.lowercase(),
                name = conv.name,
                avatarUrl = conv.avatarUrl,
                lastMessagePreview = lastMessage?.content?.take(100),
                lastMessageAt = lastMessage?.serverTimestamp?.toString(),
                unreadCount = unreadCountMap[conv.id] ?: 0,
                participantIds = members.map { it.userId },
                disappearAfterSeconds = conv.disappearAfterSeconds,
                isPinned = myMember?.pinned ?: false
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

    // ─── Helpers ──────────────────────────────────────────────

    private fun sortUuids(a: UUID, b: UUID): Pair<UUID, UUID> {
        return if (a.toString() < b.toString()) Pair(a, b) else Pair(b, a)
    }
}

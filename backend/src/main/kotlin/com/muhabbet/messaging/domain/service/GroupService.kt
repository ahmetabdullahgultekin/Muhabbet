package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.GroupMemberInfo
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class GroupService(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val messageBroadcaster: MessageBroadcaster
) : ManageGroupUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

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

        // Validate all users exist (batch query instead of N individual lookups)
        val validUsers = userRepository.findAllByIds(newUserIds)
        if (validUsers.size != newUserIds.size) {
            throw BusinessException(ErrorCode.CONV_INVALID_PARTICIPANTS)
        }
        val usersMap = validUsers.associateBy { it.id }

        val addedMembers = newUserIds.map { uid ->
            conversationRepository.saveMember(
                ConversationMember(conversationId = conversationId, userId = uid, role = MemberRole.MEMBER)
            )
        }

        // Broadcast to all group members (use pre-loaded users map)
        val allMemberIds = (existingUserIds + newUserIds).toList()
        val memberInfos = addedMembers.map { m ->
            val user = usersMap[m.userId]
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
}

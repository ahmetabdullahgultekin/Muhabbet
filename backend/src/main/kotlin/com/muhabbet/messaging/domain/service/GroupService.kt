package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.GroupMemberInfo
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Every mutation here follows the boundary [MessageService] established in #491 and #669 finished:
 * the persistence half runs inside [TransactionRunner.inTransaction], and the WebSocket fan-out
 * happens after that has committed.
 *
 * None of those methods may carry `@Transactional` as well. [TransactionRunner] propagates as
 * REQUIRED, so an enclosing transaction would simply absorb the inner one and the boundary would be
 * decorative — the connection would still be held across the fan-out, which is the thing being
 * fixed.
 *
 * The trade is the same at every site: a broadcast that fails after the commit means a membership
 * change nobody was pushed, and a client that had the group open sees it on its next load. The
 * shape it replaces could hand every member a `GroupMemberAdded` for a row a rollback then removed,
 * which no later load corrects.
 */
open class GroupService(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val messageBroadcaster: MessageBroadcaster,
    private val blockPolicy: BlockPolicyPort,
    private val transactions: TransactionRunner
) : ManageGroupUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    private val permissions = GroupPermissions(conversationRepository)

    override fun addMembers(conversationId: UUID, requesterId: UUID, userIds: List<UUID>): List<ConversationMember> {
        val outcome = transactions.inTransaction { persistAddMembers(conversationId, requesterId, userIds) }

        messageBroadcaster.broadcastToUsers(
            outcome.recipients,
            WsMessage.GroupMemberAdded(
                conversationId = conversationId.toString(),
                addedBy = requesterId.toString(),
                members = outcome.memberInfos
            )
        )

        log.info("Members added to group {}: {}", conversationId, outcome.added.map { it.userId })
        return outcome.added
    }

    /** What the add transaction produced: the new rows, who to tell, and what to tell them. */
    private data class AddOutcome(
        val added: List<ConversationMember>,
        val recipients: List<UUID>,
        val memberInfos: List<GroupMemberInfo>
    )

    private fun persistAddMembers(
        conversationId: UUID,
        requesterId: UUID,
        userIds: List<UUID>
    ): AddOutcome {
        permissions.requireGroup(conversationId)
        permissions.requireAdminOrOwner(conversationId, requesterId)

        val existingMembers = conversationRepository.findMembersByConversationId(conversationId)
        val existingUserIds = existingMembers.map { it.userId }.toSet()

        val newUserIds = userIds.filter { it !in existingUserIds }
        if (newUserIds.isEmpty()) throw BusinessException(ErrorCode.GROUP_ALREADY_MEMBER)

        if (existingMembers.size + newUserIds.size > ValidationRules.MAX_GROUP_MEMBERS) {
            throw BusinessException(ErrorCode.CONV_MAX_MEMBERS)
        }

        val usersMap = vetInvitees(conversationId, requesterId, newUserIds)

        val addedMembers = newUserIds.map { uid ->
            conversationRepository.saveMember(
                ConversationMember(conversationId = conversationId, userId = uid, role = MemberRole.MEMBER)
            )
        }

        // Everyone in the group afterwards, the new arrivals included (use pre-loaded users map).
        val allMemberIds = (existingUserIds + newUserIds).toList()
        val memberInfos = addedMembers.map { m ->
            val user = usersMap[m.userId]
            GroupMemberInfo(userId = m.userId.toString(), displayName = user?.displayName, role = m.role.name)
        }

        return AddOutcome(addedMembers, allMemberIds, memberInfos)
    }

    override fun removeMember(conversationId: UUID, requesterId: UUID, targetUserId: UUID) {
        val recipients = transactions.inTransaction {
            persistRemoveMember(conversationId, requesterId, targetUserId)
        }

        messageBroadcaster.broadcastToUsers(
            recipients,
            WsMessage.GroupMemberRemoved(
                conversationId = conversationId.toString(),
                removedBy = requesterId.toString(),
                userId = targetUserId.toString()
            )
        )

        log.info("Member {} removed from group {} by {}", targetUserId, conversationId, requesterId)
    }

    private fun persistRemoveMember(
        conversationId: UUID,
        requesterId: UUID,
        targetUserId: UUID
    ): List<UUID> {
        permissions.requireGroup(conversationId)

        val targetMember = conversationRepository.findMember(conversationId, targetUserId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)

        if (targetMember.role == MemberRole.OWNER) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_REMOVE_OWNER)
        }

        permissions.requireCanRemove(conversationId, requesterId, targetMember)

        conversationRepository.removeMember(conversationId, targetUserId)

        // Remaining members, plus the removed user — who has to be told they are out.
        val members = conversationRepository.findMembersByConversationId(conversationId)
        return members.map { it.userId } + targetUserId
    }

    override fun updateGroupInfo(
        conversationId: UUID,
        requesterId: UUID,
        name: String?,
        description: String?,
        avatarUrl: String?
    ): Conversation {
        val outcome = transactions.inTransaction {
            persistGroupInfo(conversationId, requesterId, name, description, avatarUrl)
        }

        messageBroadcaster.broadcastToUsers(
            outcome.recipients,
            WsMessage.GroupInfoUpdated(
                conversationId = conversationId.toString(),
                updatedBy = requesterId.toString(),
                name = name,
                avatarUrl = avatarUrl,
                description = description
            )
        )

        log.info("Group info updated: {}", conversationId)
        return outcome.conversation
    }

    /** What the group-info transaction produced: the saved conversation, and who to tell. */
    private data class GroupInfoOutcome(val conversation: Conversation, val recipients: List<UUID>)

    private fun persistGroupInfo(
        conversationId: UUID,
        requesterId: UUID,
        name: String?,
        description: String?,
        avatarUrl: String?
    ): GroupInfoOutcome {
        val conversation = permissions.requireGroup(conversationId)
        permissions.requireAdminOrOwner(conversationId, requesterId)

        if (name != null && !ValidationRules.isValidGroupName(name)) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR, "Geçersiz grup adı")
        }

        val updated = conversation.copy(
            name = name ?: conversation.name,
            description = description ?: conversation.description,
            avatarUrl = avatarUrl ?: conversation.avatarUrl,
            updatedAt = Instant.now()
        )
        val saved = conversationRepository.updateConversation(updated)

        val memberIds = conversationRepository.findMembersByConversationId(conversationId).map { it.userId }
        return GroupInfoOutcome(saved, memberIds)
    }

    override fun updateMemberRole(conversationId: UUID, requesterId: UUID, targetUserId: UUID, newRole: MemberRole) {
        val recipients = transactions.inTransaction {
            persistMemberRole(conversationId, requesterId, targetUserId, newRole)
        }

        messageBroadcaster.broadcastToUsers(
            recipients,
            WsMessage.GroupRoleUpdated(
                conversationId = conversationId.toString(),
                updatedBy = requesterId.toString(),
                userId = targetUserId.toString(),
                newRole = newRole.name
            )
        )

        log.info("Member {} role updated to {} in group {} by {}", targetUserId, newRole, conversationId, requesterId)
    }

    private fun persistMemberRole(
        conversationId: UUID,
        requesterId: UUID,
        targetUserId: UUID,
        newRole: MemberRole
    ): List<UUID> {
        permissions.requireGroup(conversationId)

        val requesterMember = permissions.requireMember(conversationId, requesterId)
        if (requesterMember.role != MemberRole.OWNER) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED, "Sadece grup sahibi rol değiştirebilir")
        }

        conversationRepository.findMember(conversationId, targetUserId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)

        conversationRepository.updateMemberRole(conversationId, targetUserId, newRole)

        return conversationRepository.findMembersByConversationId(conversationId).map { it.userId }
    }

    override fun leaveGroup(conversationId: UUID, userId: UUID) {
        val outcome = transactions.inTransaction { persistLeaveGroup(conversationId, userId) }

        // A null recipient list is the last-member case: the group is empty, so there is nobody
        // left to tell.
        outcome.recipients?.let { recipients ->
            messageBroadcaster.broadcastToUsers(
                recipients,
                WsMessage.GroupMemberLeft(conversationId = conversationId.toString(), userId = userId.toString())
            )
            log.info("User {} left group {}", userId, conversationId)
        }
    }

    /**
     * What the leave transaction produced. A null [recipients] means the leaver was the last
     * member, so nothing goes out — as distinct from an empty list, which would say the same thing
     * by accident rather than by decision.
     */
    private data class LeaveOutcome(val recipients: List<UUID>?)

    private fun persistLeaveGroup(conversationId: UUID, userId: UUID): LeaveOutcome {
        permissions.requireGroup(conversationId)

        val member = permissions.requireMember(conversationId, userId)

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
                return LeaveOutcome(recipients = null)
            }

            conversationRepository.updateMemberRole(conversationId, newOwner.userId, MemberRole.OWNER)
            log.info("Ownership transferred to {} in group {}", newOwner.userId, conversationId)
        }

        conversationRepository.removeMember(conversationId, userId)

        val remainingMembers = conversationRepository.findMembersByConversationId(conversationId)
        return LeaveOutcome(remainingMembers.map { it.userId })
    }

    @Transactional
    override fun setAnnouncementMode(conversationId: UUID, requesterId: UUID, enabled: Boolean): Conversation {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

        // A DIRECT conversation has no admins, so "only admins may post" would silence both people
        // with no way back. The route used to accept one.
        if (conversation.type == ConversationType.DIRECT) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT)
        }

        permissions.requireAdminOrOwner(conversationId, requesterId)

        val saved = conversationRepository.updateConversation(
            conversation.copy(announcementOnly = enabled, updatedAt = Instant.now())
        )
        log.info("Announcement mode set to {} on conversation {} by {}", enabled, conversationId, requesterId)
        return saved
    }

    // ─── Helpers ──────────────────────────────────────────────

    /**
     * The invitees, keyed by id, once both ways an add can be refused have been ruled out.
     *
     * Both raise CONV_INVALID_PARTICIPANTS, the code an unresolvable user id gets, rather than the
     * block getting one of its own. A distinct code would be a reliable one-bit oracle: create a
     * throwaway group, add the target, read the code, and you know they blocked you. Sharing a code
     * with "no such user" is what makes the answer ambiguous — the wording of a message cannot do
     * that, only the code can.
     *
     * The whole batch is refused either way: addMembers has always been all-or-nothing, and a
     * partial success is the harder thing for a caller to notice. Only the *added* user's block
     * counts; a requester adding someone they blocked themselves is their own business.
     */
    private fun vetInvitees(
        conversationId: UUID,
        requesterId: UUID,
        newUserIds: List<UUID>
    ): Map<UUID, User> {
        // Batch query instead of N individual lookups.
        val validUsers = userRepository.findAllByIds(newUserIds)
        if (validUsers.size != newUserIds.size) {
            throw BusinessException(ErrorCode.CONV_INVALID_PARTICIPANTS)
        }

        if (newUserIds.any { blockPolicy.hasBlocked(it, requesterId) }) {
            log.info("Group add refused, an invitee has blocked the requester: conv={}, requester={}", conversationId, requesterId)
            throw BusinessException(ErrorCode.CONV_INVALID_PARTICIPANTS)
        }

        return validUsers.associateBy { it.id }
    }
}

/**
 * Who may do what in a group.
 *
 * Split out of [GroupService] (#669) because rank is a question about the group, not about the
 * mutation being attempted: the service had grown to sixteen functions, of which four did nothing
 * but ask it, and the same two guards opened all five mutations. Kept in this file and private to
 * it — it is a helper for one class, not a port, and giving it a package of its own would suggest
 * otherwise.
 */
private class GroupPermissions(private val conversationRepository: ConversationRepository) {

    /**
     * The conversation, if it is a group that can be administered at all.
     *
     * All five [GroupService] mutations opened with these same two guards. A DIRECT conversation
     * has no admins and no membership to change, so refusing it here rather than five times over is
     * both the DRY answer and the one that cannot drift apart between methods.
     */
    fun requireGroup(conversationId: UUID): Conversation {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT)
        }
        return conversation
    }

    /**
     * An ADMIN may remove a MEMBER, an OWNER may remove anyone but another OWNER, and a MEMBER may
     * remove nobody. One condition rather than two guard clauses, because the two were never
     * independent — they are one rule about rank.
     */
    fun requireCanRemove(conversationId: UUID, requesterId: UUID, target: ConversationMember) {
        val requester = requireMember(conversationId, requesterId)
        val outranked = requester.role == MemberRole.MEMBER ||
            (requester.role == MemberRole.ADMIN && target.role == MemberRole.ADMIN)
        if (outranked) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
    }

    fun requireMember(conversationId: UUID, userId: UUID): ConversationMember {
        return conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)
    }

    fun requireAdminOrOwner(conversationId: UUID, userId: UUID): ConversationMember {
        val member = requireMember(conversationId, userId)
        if (member.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
        return member
    }
}

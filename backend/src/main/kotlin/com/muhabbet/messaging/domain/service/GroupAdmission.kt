package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.GroupMemberInfo
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.validation.ValidationRules
import java.util.UUID

/**
 * Letting one person into a group, the same way whichever door they came through (#888).
 *
 * Joining by invite link and being approved from a join request each used to insert the member row
 * on their own, and neither re-checked the member cap or told the group anything — so a group could
 * pass `MAX_GROUP_MEMBERS` through a link, and the existing members never learned someone had
 * joined. Both doors now come through here: [admit] is the persistence half and must run inside the
 * caller's transaction; [announce] is the fan-out and must run after it commits, the boundary
 * [GroupService] keeps for its own mutations.
 *
 * Not an in-port: it is a collaborator of two services, not something a controller asks for.
 */
class GroupAdmission(
    private val conversationRepository: ConversationRepository,
    private val userDirectory: UserDirectoryPort,
    private val messageBroadcaster: MessageBroadcaster
) {

    /** What [admit] did: the new row, and everyone who must hear about it (the newcomer included). */
    data class Admitted(val member: ConversationMember, val recipients: List<UUID>)

    /**
     * Adds [userId] to [conversationId] as a MEMBER. Refuses anything but a GROUP, and a group
     * already at the cap. Returns null when [userId] is already a member, so a caller for whom that
     * is not an error (approving a request for someone who has since joined another way) can treat
     * it as done.
     */
    fun admit(conversationId: UUID, userId: UUID): Admitted? {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)
        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT)
        }

        val members = conversationRepository.findMembersByConversationId(conversationId)
        if (members.any { it.userId == userId }) return null
        if (members.size >= ValidationRules.MAX_GROUP_MEMBERS) {
            throw BusinessException(ErrorCode.CONV_MAX_MEMBERS)
        }

        val member = conversationRepository.saveMember(
            ConversationMember(conversationId = conversationId, userId = userId, role = MemberRole.MEMBER)
        )
        return Admitted(member, members.map { it.userId } + userId)
    }

    /** Tells the group, after commit, that [admitted] joined; [addedBy] is the admin or the joiner. */
    fun announce(admitted: Admitted, addedBy: UUID) {
        val member = admitted.member
        val displayName = userDirectory.findDisplayInfo(listOf(member.userId))[member.userId]?.displayName
        messageBroadcaster.broadcastToUsers(
            admitted.recipients,
            WsMessage.GroupMemberAdded(
                conversationId = member.conversationId.toString(),
                addedBy = addedBy.toString(),
                members = listOf(
                    GroupMemberInfo(userId = member.userId.toString(), displayName = displayName, role = member.role.name)
                )
            )
        )
    }
}

package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.MemberRole
import java.util.UUID

interface ManageGroupUseCase {
    fun addMembers(conversationId: UUID, requesterId: UUID, userIds: List<UUID>): List<ConversationMember>
    fun removeMember(conversationId: UUID, requesterId: UUID, targetUserId: UUID)
    fun updateGroupInfo(
        conversationId: UUID,
        requesterId: UUID,
        name: String?,
        description: String?,
        avatarUrl: String? = null
    ): Conversation
    fun updateMemberRole(conversationId: UUID, requesterId: UUID, targetUserId: UUID, newRole: MemberRole)
    fun leaveGroup(conversationId: UUID, userId: UUID)

    /**
     * Turns "only admins may post" on or off, returning the conversation as stored.
     *
     * Lives here rather than in the controller (where the whole check used to sit inline) because
     * it is a permission decision: who may flip it, and on what kind of conversation, is business
     * logic. The enforcement half already lived in the domain — `MessageService` refuses a MEMBER
     * sending to an `announcementOnly` conversation with `MSG_ANNOUNCEMENT_ONLY` — and the two
     * halves of one rule belong at the same layer.
     */
    fun setAnnouncementMode(conversationId: UUID, requesterId: UUID, enabled: Boolean): Conversation
}

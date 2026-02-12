package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.MemberRole
import java.util.UUID

interface ManageGroupUseCase {
    fun addMembers(conversationId: UUID, requesterId: UUID, userIds: List<UUID>): List<ConversationMember>
    fun removeMember(conversationId: UUID, requesterId: UUID, targetUserId: UUID)
    fun updateGroupInfo(conversationId: UUID, requesterId: UUID, name: String?, description: String?): Conversation
    fun updateMemberRole(conversationId: UUID, requesterId: UUID, targetUserId: UUID, newRole: MemberRole)
    fun leaveGroup(conversationId: UUID, userId: UUID)
}

package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.GroupInviteLink
import java.util.UUID

interface GroupInviteLinkRepository {
    fun save(link: GroupInviteLink): GroupInviteLink
    fun findById(id: UUID): GroupInviteLink?
    fun findByToken(token: String): GroupInviteLink?
    fun findActiveByConversationId(conversationId: UUID): List<GroupInviteLink>
    fun deactivate(id: UUID)
    fun incrementUseCount(id: UUID)
}

package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupInviteLinkJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataGroupInviteLinkRepository : JpaRepository<GroupInviteLinkJpaEntity, UUID> {
    fun findByInviteTokenAndIsActiveTrue(inviteToken: String): GroupInviteLinkJpaEntity?
    fun findByConversationIdAndIsActiveTrue(conversationId: UUID): List<GroupInviteLinkJpaEntity>
}

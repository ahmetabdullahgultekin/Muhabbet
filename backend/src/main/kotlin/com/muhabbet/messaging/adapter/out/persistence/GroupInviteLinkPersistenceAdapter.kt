package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupInviteLinkJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataGroupInviteLinkRepository
import com.muhabbet.messaging.domain.model.GroupInviteLink
import com.muhabbet.messaging.domain.port.out.GroupInviteLinkRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GroupInviteLinkPersistenceAdapter(
    private val repo: SpringDataGroupInviteLinkRepository
) : GroupInviteLinkRepository {

    override fun save(link: GroupInviteLink): GroupInviteLink =
        repo.save(GroupInviteLinkJpaEntity.fromDomain(link)).toDomain()

    override fun findById(id: UUID): GroupInviteLink? =
        repo.findById(id).orElse(null)?.toDomain()

    override fun findByToken(token: String): GroupInviteLink? =
        repo.findByInviteTokenAndIsActiveTrue(token)?.toDomain()

    override fun findActiveByConversationId(conversationId: UUID): List<GroupInviteLink> =
        repo.findByConversationIdAndIsActiveTrue(conversationId).map { it.toDomain() }

    override fun deactivate(id: UUID) {
        val entity = repo.findById(id).orElse(null) ?: return
        entity.isActive = false
        repo.save(entity)
    }

    override fun incrementUseCount(id: UUID) {
        val entity = repo.findById(id).orElse(null) ?: return
        entity.useCount += 1
        repo.save(entity)
    }
}

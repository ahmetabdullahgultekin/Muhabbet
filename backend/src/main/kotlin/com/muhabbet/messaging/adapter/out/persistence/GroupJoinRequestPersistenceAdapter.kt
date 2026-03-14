package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupJoinRequestJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataGroupJoinRequestRepository
import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import com.muhabbet.messaging.domain.port.out.GroupJoinRequestRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GroupJoinRequestPersistenceAdapter(
    private val repo: SpringDataGroupJoinRequestRepository
) : GroupJoinRequestRepository {

    override fun save(request: GroupJoinRequest): GroupJoinRequest =
        repo.save(GroupJoinRequestJpaEntity.fromDomain(request)).toDomain()

    override fun findById(id: UUID): GroupJoinRequest? =
        repo.findById(id).orElse(null)?.toDomain()

    override fun findPendingByConversationId(conversationId: UUID): List<GroupJoinRequest> =
        repo.findByConversationIdAndStatus(conversationId, JoinRequestStatus.PENDING).map { it.toDomain() }

    override fun findByConversationIdAndUserIdAndStatus(conversationId: UUID, userId: UUID, status: JoinRequestStatus): GroupJoinRequest? =
        repo.findByConversationIdAndUserIdAndStatus(conversationId, userId, status)?.toDomain()
}

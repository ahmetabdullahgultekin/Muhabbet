package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupCallParticipantJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataGroupCallParticipantRepository
import com.muhabbet.messaging.domain.model.GroupCallParticipant
import com.muhabbet.messaging.domain.port.out.GroupCallParticipantRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class GroupCallParticipantPersistenceAdapter(
    private val repo: SpringDataGroupCallParticipantRepository
) : GroupCallParticipantRepository {

    override fun save(participant: GroupCallParticipant): GroupCallParticipant =
        repo.save(GroupCallParticipantJpaEntity.fromDomain(participant)).toDomain()

    override fun findByCallId(callId: String): List<GroupCallParticipant> =
        repo.findByCallId(callId).map { it.toDomain() }

    override fun markLeft(callId: String, userId: UUID) {
        val entity = repo.findByCallId(callId).firstOrNull { it.userId == userId } ?: return
        entity.leftAt = Instant.now()
        repo.save(entity)
    }
}

package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupEventJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.GroupEventRsvpJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataGroupEventJpaRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataGroupEventRsvpRepository
import com.muhabbet.messaging.domain.model.GroupEvent
import com.muhabbet.messaging.domain.model.GroupEventRsvp
import com.muhabbet.messaging.domain.model.RsvpStatus
import com.muhabbet.messaging.domain.port.out.GroupEventRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GroupEventPersistenceAdapter(
    private val eventRepo: SpringDataGroupEventJpaRepository,
    private val rsvpRepo: SpringDataGroupEventRsvpRepository
) : GroupEventRepository {

    override fun save(event: GroupEvent): GroupEvent =
        eventRepo.save(GroupEventJpaEntity.fromDomain(event)).toDomain()

    override fun findById(id: UUID): GroupEvent? =
        eventRepo.findById(id).orElse(null)?.toDomain()

    override fun findByConversationId(conversationId: UUID): List<GroupEvent> =
        eventRepo.findByConversationId(conversationId).map { it.toDomain() }

    override fun delete(id: UUID) =
        eventRepo.deleteById(id)

    override fun saveRsvp(rsvp: GroupEventRsvp): GroupEventRsvp =
        rsvpRepo.save(GroupEventRsvpJpaEntity.fromDomain(rsvp)).toDomain()

    override fun findRsvpsByEventId(eventId: UUID): List<GroupEventRsvp> =
        rsvpRepo.findByEventId(eventId).map { it.toDomain() }

    override fun countGoingRsvps(eventIds: List<UUID>): Map<UUID, Int> {
        // An empty IN list is not valid SQL on every dialect, and the answer is knowable anyway.
        if (eventIds.isEmpty()) return emptyMap()
        return rsvpRepo.countByStatusGroupedByEventId(eventIds, RsvpStatus.GOING)
            .associate { row -> row[0] as UUID to (row[1] as Number).toInt() }
    }
}

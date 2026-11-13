package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataStatusViewRepository
import com.muhabbet.messaging.domain.port.out.StatusViewRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class StatusViewPersistenceAdapter(
    private val repository: SpringDataStatusViewRepository
) : StatusViewRepository {

    override fun recordView(statusId: UUID, viewerId: UUID, viewedAt: Instant) {
        repository.insertIfAbsent(statusId, viewerId, viewedAt)
    }

    override fun findViewedStatusIds(viewerId: UUID, statusIds: Collection<UUID>): Set<UUID> {
        if (statusIds.isEmpty()) return emptySet()
        return repository.findViewedStatusIds(viewerId, statusIds.distinct()).toSet()
    }
}

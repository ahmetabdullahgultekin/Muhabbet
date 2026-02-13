package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.CallHistoryJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCallHistoryRepository
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import com.muhabbet.messaging.domain.service.CallHistoryRecord
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CallHistoryPersistenceAdapter(
    private val callHistoryRepo: SpringDataCallHistoryRepository
) : CallHistoryRepository {

    override fun save(record: CallHistoryRecord): CallHistoryRecord =
        callHistoryRepo.save(CallHistoryJpaEntity.fromDomain(record)).toDomain()

    override fun findByUserId(userId: UUID, limit: Int, offset: Int): List<CallHistoryRecord> {
        val page = offset / limit.coerceAtLeast(1)
        return callHistoryRepo.findByUserId(userId, PageRequest.of(page, limit))
            .map { it.toDomain() }
    }

    override fun countByUserId(userId: UUID): Int =
        callHistoryRepo.countByUserId(userId)
}

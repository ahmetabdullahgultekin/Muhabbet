package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.StatusJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataStatusRepository
import com.muhabbet.messaging.domain.model.Status
import com.muhabbet.messaging.domain.port.out.StatusRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class StatusPersistenceAdapter(
    private val statusRepo: SpringDataStatusRepository
) : StatusRepository {

    override fun save(status: Status): Status =
        statusRepo.save(StatusJpaEntity.fromDomain(status)).toDomain()

    override fun findById(id: UUID): Status? =
        statusRepo.findById(id).orElse(null)?.toDomain()

    override fun findActiveByUserId(userId: UUID): List<Status> =
        statusRepo.findActiveByUserId(userId, Instant.now()).map { it.toDomain() }

    override fun findAllActive(): List<Status> {
        val now = Instant.now()
        return statusRepo.findAll()
            .filter { it.expiresAt.isAfter(now) }
            .map { it.toDomain() }
    }

    override fun delete(id: UUID) {
        statusRepo.deleteById(id)
    }
}

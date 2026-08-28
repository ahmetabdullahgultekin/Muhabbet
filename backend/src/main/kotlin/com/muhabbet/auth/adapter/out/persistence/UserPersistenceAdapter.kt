package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.UserJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataUserRepository
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UserPersistenceAdapter(
    private val springDataUserRepository: SpringDataUserRepository
) : UserRepository {

    override fun findByPhoneNumber(phoneNumber: String): User? =
        springDataUserRepository.findByPhoneNumber(phoneNumber)?.toDomain()

    override fun findById(id: UUID): User? =
        springDataUserRepository.findById(id).orElse(null)?.toDomain()

    override fun findAllByIds(ids: List<UUID>): List<User> =
        springDataUserRepository.findAllById(ids).map { it.toDomain() }

    override fun save(user: User): User =
        springDataUserRepository.save(UserJpaEntity.fromDomain(user)).toDomain()

    override fun existsByPhoneNumber(phoneNumber: String): Boolean =
        springDataUserRepository.existsByPhoneNumber(phoneNumber)

    /**
     * A `@Modifying` query, so it needs a transaction — and it does not open one here. The boundary
     * belongs on the service layer, which is where the project's layering rules put it and where
     * [com.muhabbet.auth.domain.service.LastSeenService] now has it (#402). Annotating the adapter
     * instead would work by accident and hide the fact that an adapter had become the place a
     * transaction begins.
     *
     * The bug this replaced: the WebSocket handler called straight through to here on every
     * disconnect, from a thread with no transaction, so the query threw `No active transaction for
     * update or delete query` every single time and `last_seen_at` never moved.
     */
    override fun updateLastSeenAt(userId: java.util.UUID, lastSeenAt: java.time.Instant) {
        springDataUserRepository.updateLastSeenAt(userId, lastSeenAt)
    }
}

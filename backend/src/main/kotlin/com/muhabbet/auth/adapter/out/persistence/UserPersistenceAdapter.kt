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

    override fun updateLastSeenAt(userId: java.util.UUID, lastSeenAt: java.time.Instant) {
        springDataUserRepository.updateLastSeenAt(userId, lastSeenAt)
    }
}

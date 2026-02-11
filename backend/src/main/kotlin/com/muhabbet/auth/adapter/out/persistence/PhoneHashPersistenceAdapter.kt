package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.PhoneHashJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataPhoneHashRepository
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PhoneHashPersistenceAdapter(
    private val springDataPhoneHashRepository: SpringDataPhoneHashRepository
) : PhoneHashRepository {

    override fun save(userId: UUID, phoneHash: String) {
        springDataPhoneHashRepository.save(PhoneHashJpaEntity(userId = userId, phoneHash = phoneHash))
    }

    override fun findUserIdsByPhoneHashes(phoneHashes: List<String>): Map<String, UUID> =
        springDataPhoneHashRepository.findByPhoneHashIn(phoneHashes)
            .associate { it.phoneHash to it.userId }
}

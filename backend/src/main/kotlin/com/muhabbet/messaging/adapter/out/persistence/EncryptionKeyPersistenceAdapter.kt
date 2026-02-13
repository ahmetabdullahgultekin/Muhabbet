package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.EncryptionKeyJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.OneTimePreKeyJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataEncryptionKeyRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataOneTimePreKeyRepository
import com.muhabbet.messaging.domain.model.EncryptionKeyBundle
import com.muhabbet.messaging.domain.model.OneTimePreKey
import com.muhabbet.messaging.domain.port.out.EncryptionKeyRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class EncryptionKeyPersistenceAdapter(
    private val encryptionKeyRepo: SpringDataEncryptionKeyRepository,
    private val oneTimePreKeyRepo: SpringDataOneTimePreKeyRepository
) : EncryptionKeyRepository {

    @Transactional
    override fun saveKeyBundle(userId: UUID, bundle: EncryptionKeyBundle): EncryptionKeyBundle {
        // Replace existing key bundle for this user (one bundle per user)
        encryptionKeyRepo.deleteByUserId(userId)
        return encryptionKeyRepo.save(EncryptionKeyJpaEntity.fromDomain(bundle)).toDomain()
    }

    override fun getKeyBundle(userId: UUID): EncryptionKeyBundle? {
        return encryptionKeyRepo.findByUserId(userId)?.toDomain()
    }

    @Transactional
    override fun saveOneTimePreKeys(userId: UUID, keys: List<OneTimePreKey>) {
        val entities = keys.map { OneTimePreKeyJpaEntity.fromDomain(it) }
        oneTimePreKeyRepo.saveAll(entities)
    }

    @Transactional
    override fun consumeOneTimePreKey(userId: UUID): OneTimePreKey? {
        val unused = oneTimePreKeyRepo.findUnusedByUserId(userId)
        val first = unused.firstOrNull() ?: return null

        // Mark as used by saving an updated entity
        val consumed = OneTimePreKeyJpaEntity(
            id = first.id,
            userId = first.userId,
            keyId = first.keyId,
            publicKey = first.publicKey,
            used = true,
            createdAt = first.createdAt
        )
        oneTimePreKeyRepo.save(consumed)
        return first.toDomain()
    }

    override fun countUnusedPreKeys(userId: UUID): Int {
        return oneTimePreKeyRepo.countByUserIdAndUsedFalse(userId)
    }
}

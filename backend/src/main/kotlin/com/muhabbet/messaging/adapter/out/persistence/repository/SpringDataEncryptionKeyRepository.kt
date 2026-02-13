package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.EncryptionKeyJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataEncryptionKeyRepository : JpaRepository<EncryptionKeyJpaEntity, UUID> {
    fun findByUserId(userId: UUID): EncryptionKeyJpaEntity?
    fun deleteByUserId(userId: UUID)
}

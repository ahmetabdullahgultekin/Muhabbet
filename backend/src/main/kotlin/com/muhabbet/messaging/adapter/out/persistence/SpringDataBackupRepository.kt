package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageBackupJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataBackupRepository : JpaRepository<MessageBackupJpaEntity, UUID> {
    fun findFirstByUserIdOrderByStartedAtDesc(userId: UUID): MessageBackupJpaEntity?
    fun findByUserIdOrderByStartedAtDesc(userId: UUID): List<MessageBackupJpaEntity>
}

package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageMentionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataMessageMentionRepository : JpaRepository<MessageMentionJpaEntity, UUID> {
    fun findByMessageIdOrderByStartOffset(messageId: UUID): List<MessageMentionJpaEntity>
    fun findByMessageIdInOrderByStartOffset(messageIds: List<UUID>): List<MessageMentionJpaEntity>
}

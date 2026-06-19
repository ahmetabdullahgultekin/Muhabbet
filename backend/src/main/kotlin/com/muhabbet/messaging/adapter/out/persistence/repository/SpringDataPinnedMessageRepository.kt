package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.PinnedMessageId
import com.muhabbet.messaging.adapter.out.persistence.entity.PinnedMessageJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataPinnedMessageRepository : JpaRepository<PinnedMessageJpaEntity, PinnedMessageId> {
    fun findByConversationIdOrderByPinnedAtDesc(conversationId: UUID): List<PinnedMessageJpaEntity>
    fun countByConversationId(conversationId: UUID): Long
}

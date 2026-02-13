package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.ConversationJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataConversationRepository : JpaRepository<ConversationJpaEntity, UUID> {
    fun findByType(type: String): List<ConversationJpaEntity>
}

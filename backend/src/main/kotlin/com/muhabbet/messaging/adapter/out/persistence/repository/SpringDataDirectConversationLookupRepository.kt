package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.DirectConversationLookupId
import com.muhabbet.messaging.adapter.out.persistence.entity.DirectConversationLookupJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataDirectConversationLookupRepository : JpaRepository<DirectConversationLookupJpaEntity, DirectConversationLookupId> {
    fun findByUserIdLowAndUserIdHigh(userIdLow: UUID, userIdHigh: UUID): DirectConversationLookupJpaEntity?
}

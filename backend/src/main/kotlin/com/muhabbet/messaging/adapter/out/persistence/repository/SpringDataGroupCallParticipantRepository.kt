package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupCallParticipantId
import com.muhabbet.messaging.adapter.out.persistence.entity.GroupCallParticipantJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataGroupCallParticipantRepository : JpaRepository<GroupCallParticipantJpaEntity, GroupCallParticipantId> {
    fun findByCallId(callId: String): List<GroupCallParticipantJpaEntity>
}

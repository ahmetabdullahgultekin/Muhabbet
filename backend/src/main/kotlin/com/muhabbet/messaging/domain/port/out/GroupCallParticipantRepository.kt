package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.GroupCallParticipant

interface GroupCallParticipantRepository {
    fun save(participant: GroupCallParticipant): GroupCallParticipant
    fun findByCallId(callId: String): List<GroupCallParticipant>
    fun markLeft(callId: String, userId: java.util.UUID)
}

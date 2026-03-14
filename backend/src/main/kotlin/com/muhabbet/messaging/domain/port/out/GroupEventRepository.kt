package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.GroupEvent
import com.muhabbet.messaging.domain.model.GroupEventRsvp
import java.util.UUID

interface GroupEventRepository {
    fun save(event: GroupEvent): GroupEvent
    fun findById(id: UUID): GroupEvent?
    fun findByConversationId(conversationId: UUID): List<GroupEvent>
    fun delete(id: UUID)

    fun saveRsvp(rsvp: GroupEventRsvp): GroupEventRsvp
    fun findRsvpsByEventId(eventId: UUID): List<GroupEventRsvp>
}

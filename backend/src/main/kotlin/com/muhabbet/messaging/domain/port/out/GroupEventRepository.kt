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

    /**
     * GOING counts for the given events, keyed by event id. Events with no GOING answer are absent
     * from the map rather than present with a zero, so callers must default.
     */
    fun countGoingRsvps(eventIds: List<UUID>): Map<UUID, Int>
}

package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.GroupEvent
import com.muhabbet.messaging.domain.model.GroupEventRsvp
import com.muhabbet.messaging.domain.model.RsvpStatus
import java.time.Instant
import java.util.UUID

interface ManageGroupEventUseCase {
    fun createEvent(conversationId: UUID, userId: UUID, title: String, description: String?, eventTime: Instant, location: String?): GroupEvent
    fun getEvents(conversationId: UUID): List<GroupEvent>
    fun deleteEvent(eventId: UUID, userId: UUID)
    fun rsvp(eventId: UUID, userId: UUID, status: RsvpStatus): GroupEventRsvp
    fun getRsvps(eventId: UUID): List<GroupEventRsvp>
}

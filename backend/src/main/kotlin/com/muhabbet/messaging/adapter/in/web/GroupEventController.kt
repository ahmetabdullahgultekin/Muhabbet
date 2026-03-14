package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.RsvpStatus
import com.muhabbet.messaging.domain.port.`in`.ManageGroupEventUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class CreateEventRequest(
    val title: String,
    val description: String? = null,
    val eventTime: String,
    val location: String? = null
)

data class RsvpRequest(val status: String)

data class GroupEventResponse(
    val id: String,
    val conversationId: String,
    val createdBy: String,
    val title: String,
    val description: String?,
    val eventTime: String,
    val location: String?,
    val createdAt: String
)

data class RsvpResponse(
    val eventId: String,
    val userId: String,
    val status: String,
    val respondedAt: String
)

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/events")
class GroupEventController(
    private val manageGroupEventUseCase: ManageGroupEventUseCase
) {

    @PostMapping
    fun createEvent(
        @PathVariable conversationId: UUID,
        @RequestBody request: CreateEventRequest
    ): ResponseEntity<ApiResponse<GroupEventResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val event = manageGroupEventUseCase.createEvent(
            conversationId = conversationId,
            userId = userId,
            title = request.title,
            description = request.description,
            eventTime = Instant.parse(request.eventTime),
            location = request.location
        )
        return ApiResponseBuilder.created(event.toResponse())
    }

    @GetMapping
    fun getEvents(@PathVariable conversationId: UUID): ResponseEntity<ApiResponse<List<GroupEventResponse>>> {
        val events = manageGroupEventUseCase.getEvents(conversationId)
        return ApiResponseBuilder.ok(events.map { it.toResponse() })
    }

    @DeleteMapping("/{eventId}")
    fun deleteEvent(
        @PathVariable conversationId: UUID,
        @PathVariable eventId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageGroupEventUseCase.deleteEvent(eventId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/{eventId}/rsvp")
    fun rsvp(
        @PathVariable conversationId: UUID,
        @PathVariable eventId: UUID,
        @RequestBody request: RsvpRequest
    ): ResponseEntity<ApiResponse<RsvpResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val status = RsvpStatus.valueOf(request.status.uppercase())
        val rsvp = manageGroupEventUseCase.rsvp(eventId, userId, status)
        return ApiResponseBuilder.ok(
            RsvpResponse(
                eventId = rsvp.eventId.toString(),
                userId = rsvp.userId.toString(),
                status = rsvp.status.name,
                respondedAt = rsvp.respondedAt.toString()
            )
        )
    }

    @GetMapping("/{eventId}/rsvps")
    fun getRsvps(
        @PathVariable conversationId: UUID,
        @PathVariable eventId: UUID
    ): ResponseEntity<ApiResponse<List<RsvpResponse>>> {
        val rsvps = manageGroupEventUseCase.getRsvps(eventId)
        return ApiResponseBuilder.ok(rsvps.map {
            RsvpResponse(
                eventId = it.eventId.toString(),
                userId = it.userId.toString(),
                status = it.status.name,
                respondedAt = it.respondedAt.toString()
            )
        })
    }
}

private fun com.muhabbet.messaging.domain.model.GroupEvent.toResponse() = GroupEventResponse(
    id = id.toString(),
    conversationId = conversationId.toString(),
    createdBy = createdBy.toString(),
    title = title,
    description = description,
    eventTime = eventTime.toString(),
    location = location,
    createdAt = createdAt.toString()
)

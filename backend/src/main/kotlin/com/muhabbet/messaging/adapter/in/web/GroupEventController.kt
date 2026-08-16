package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.GroupEventSummary
import com.muhabbet.messaging.domain.model.RsvpStatus
import com.muhabbet.messaging.domain.port.`in`.ManageGroupEventUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.CreateGroupEventRequest
import com.muhabbet.shared.dto.GroupEventResponse
import com.muhabbet.shared.dto.RsvpRequest
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
        @RequestBody request: CreateGroupEventRequest
    ): ResponseEntity<ApiResponse<GroupEventResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val event = manageGroupEventUseCase.createEvent(
            conversationId = conversationId,
            userId = userId,
            title = request.title,
            description = request.description,
            eventTime = Instant.ofEpochMilli(request.eventTime),
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

/**
 * Maps to the shared [GroupEventResponse] the mobile client actually deserializes. `eventTime` is
 * epoch millis on both sides — this controller used to publish an ISO-8601 string and parse one
 * back, which no client ever sent (#498).
 */
private fun GroupEventSummary.toResponse() = GroupEventResponse(
    id = event.id.toString(),
    title = event.title,
    description = event.description,
    eventTime = event.eventTime.toEpochMilli(),
    location = event.location,
    createdBy = event.createdBy.toString(),
    goingCount = goingCount,
    createdAt = event.createdAt.toString()
)

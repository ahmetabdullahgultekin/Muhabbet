package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.GroupEvent
import com.muhabbet.messaging.domain.model.GroupEventSummary
import com.muhabbet.messaging.domain.port.`in`.ManageGroupEventUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.dto.CreateGroupEventRequest
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * The endpoint the mobile client calls to create a group event. It had no test at all, which is how
 * it shipped parsing an ISO-8601 string out of a field the client has always sent as epoch millis
 * (#498) — every real request 500'd on `DateTimeParseException`.
 */
class GroupEventControllerTest {

    private lateinit var manageGroupEventUseCase: ManageGroupEventUseCase
    private lateinit var controller: GroupEventController

    private val userId = TestData.USER_ID_1
    private val groupId = TestData.GROUP_ID

    /** 2026-08-16T19:11:43.882Z — the value from the production stack trace in #498. */
    private val eventTimeMillis = 1786993903882L

    @BeforeEach
    fun setUp() {
        manageGroupEventUseCase = mockk()
        controller = GroupEventController(manageGroupEventUseCase)
        val claims = JwtClaims(userId = userId, deviceId = TestData.DEVICE_ID_1)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(claims, null, emptyList())
    }

    private fun event(
        id: UUID = UUID.randomUUID(),
        eventTime: Instant = Instant.ofEpochMilli(eventTimeMillis)
    ) = GroupEvent(
        id = id,
        conversationId = groupId,
        createdBy = userId,
        title = "Toplantı",
        description = "Haftalık",
        eventTime = eventTime,
        location = "Ofis"
    )

    @Nested
    inner class CreateEvent {

        @Test
        fun `should accept epoch millis as the event time`() {
            val instant = slot<Instant>()
            every {
                manageGroupEventUseCase.createEvent(groupId, userId, any(), any(), capture(instant), any())
            } answers { GroupEventSummary(event(), goingCount = 0) }

            assertDoesNotThrow {
                controller.createEvent(
                    groupId,
                    CreateGroupEventRequest(
                        title = "Toplantı",
                        description = "Haftalık",
                        eventTime = eventTimeMillis,
                        location = "Ofis"
                    )
                )
            }

            assertEquals(Instant.ofEpochMilli(eventTimeMillis), instant.captured)
        }

        @Test
        fun `should return 201 with the event time as epoch millis`() {
            every {
                manageGroupEventUseCase.createEvent(groupId, userId, any(), any(), any(), any())
            } returns GroupEventSummary(event(), goingCount = 0)

            val response = controller.createEvent(
                groupId,
                CreateGroupEventRequest(title = "Toplantı", eventTime = eventTimeMillis)
            )

            assertEquals(201, response.statusCode.value())
            assertEquals(eventTimeMillis, response.body?.data?.eventTime)
        }

        @Test
        fun `should report a brand new event as having nobody going`() {
            every {
                manageGroupEventUseCase.createEvent(groupId, userId, any(), any(), any(), any())
            } returns GroupEventSummary(event(), goingCount = 0)

            val response = controller.createEvent(
                groupId,
                CreateGroupEventRequest(title = "Toplantı", eventTime = eventTimeMillis)
            )

            // goingCount has no default in the shared DTO, so omitting it would fail the client's
            // deserialization outright — the create would "succeed" and still surface as an error.
            assertEquals(0, response.body?.data?.goingCount)
        }

        @Test
        fun `should pass the optional fields through untouched`() {
            every {
                manageGroupEventUseCase.createEvent(groupId, userId, "Toplantı", null, any(), null)
            } returns GroupEventSummary(event(), goingCount = 0)

            controller.createEvent(
                groupId,
                CreateGroupEventRequest(title = "Toplantı", eventTime = eventTimeMillis)
            )

            verify {
                manageGroupEventUseCase.createEvent(groupId, userId, "Toplantı", null, any(), null)
            }
        }
    }

    @Nested
    inner class GetEvents {

        @Test
        fun `should return each event with its going count`() {
            val first = event()
            val second = event()
            every { manageGroupEventUseCase.getEvents(groupId) } returns listOf(
                GroupEventSummary(first, goingCount = 3),
                GroupEventSummary(second, goingCount = 0)
            )

            val response = controller.getEvents(groupId)
            val data = response.body?.data

            assertEquals(2, data?.size)
            assertEquals(3, data?.get(0)?.goingCount)
            assertEquals(0, data?.get(1)?.goingCount)
            assertEquals(first.id.toString(), data?.get(0)?.id)
        }

        @Test
        fun `should publish event times as epoch millis`() {
            every { manageGroupEventUseCase.getEvents(groupId) } returns listOf(
                GroupEventSummary(event(), goingCount = 1)
            )

            val response = controller.getEvents(groupId)

            assertEquals(eventTimeMillis, response.body?.data?.first()?.eventTime)
        }
    }
}

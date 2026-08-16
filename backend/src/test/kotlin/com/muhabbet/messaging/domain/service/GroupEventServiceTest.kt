package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.GroupEvent
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupEventRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class GroupEventServiceTest {

    private lateinit var groupEventRepository: GroupEventRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var groupEventService: GroupEventService

    private val groupId = TestData.GROUP_ID
    private val userId = TestData.USER_ID_1
    private val eventTime = Instant.ofEpochMilli(1786993903882L)

    @BeforeEach
    fun setUp() {
        groupEventRepository = mockk()
        conversationRepository = mockk()
        groupEventService = GroupEventService(groupEventRepository, conversationRepository)
    }

    private fun event(id: UUID = UUID.randomUUID()) = GroupEvent(
        id = id,
        conversationId = groupId,
        createdBy = userId,
        title = "Toplantı",
        eventTime = eventTime
    )

    @Nested
    inner class CreateEvent {

        @Test
        fun `should report a brand new event as having nobody going`() {
            every { conversationRepository.findMember(groupId, userId) } returns
                    TestData.member(conversationId = groupId, userId = userId)
            every { groupEventRepository.save(any()) } answers { firstArg() }

            val result = groupEventService.createEvent(
                conversationId = groupId,
                userId = userId,
                title = "Toplantı",
                description = null,
                eventTime = eventTime,
                location = null
            )

            assertEquals(0, result.goingCount)
            assertEquals("Toplantı", result.event.title)
            assertEquals(eventTime, result.event.eventTime)
        }

        @Test
        fun `should reject a non-member`() {
            every { conversationRepository.findMember(groupId, userId) } returns null

            assertThrows<BusinessException> {
                groupEventService.createEvent(groupId, userId, "Toplantı", null, eventTime, null)
            }

            verify(exactly = 0) { groupEventRepository.save(any()) }
        }
    }

    @Nested
    inner class GetEvents {

        @Test
        fun `should attach the going count to each event`() {
            val busy = event()
            val quiet = event()
            every { groupEventRepository.findByConversationId(groupId) } returns listOf(busy, quiet)
            every { groupEventRepository.countGoingRsvps(listOf(busy.id, quiet.id)) } returns
                    mapOf(busy.id to 4)

            val result = groupEventService.getEvents(groupId)

            assertEquals(4, result.first { it.event.id == busy.id }.goingCount)
            // Absent from the count map means nobody answered GOING, not "unknown".
            assertEquals(0, result.first { it.event.id == quiet.id }.goingCount)
        }

        @Test
        fun `should ask for counts once for the whole page rather than once per event`() {
            val first = event()
            val second = event()
            val third = event()
            every { groupEventRepository.findByConversationId(groupId) } returns
                    listOf(first, second, third)
            every { groupEventRepository.countGoingRsvps(any()) } returns emptyMap()

            groupEventService.getEvents(groupId)

            verify(exactly = 1) { groupEventRepository.countGoingRsvps(any()) }
            verify(exactly = 0) { groupEventRepository.findRsvpsByEventId(any()) }
        }

        @Test
        fun `should return nothing for a group with no events`() {
            every { groupEventRepository.findByConversationId(groupId) } returns emptyList()
            every { groupEventRepository.countGoingRsvps(emptyList()) } returns emptyMap()

            assertTrue(groupEventService.getEvents(groupId).isEmpty())
        }
    }
}

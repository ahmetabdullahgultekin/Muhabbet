package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.GroupEvent
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupEventRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * A group's events say when and where its members will physically meet, and the RSVPs say who.
 * Both are for the group's members only, exactly like creating or answering an event already was.
 */
class GroupEventReadAuthorizationTest {

    private lateinit var events: GroupEventRepository
    private lateinit var conversations: ConversationRepository
    private lateinit var service: GroupEventService

    private val groupId = TestData.GROUP_ID
    private val outsider = TestData.USER_ID_3
    private val member = TestData.USER_ID_2

    @BeforeEach
    fun setUp() {
        events = mockk(relaxed = true)
        conversations = mockk()
        service = GroupEventService(events, conversations)
        every { conversations.findMember(groupId, outsider) } returns null
        every { conversations.findMember(groupId, member) } returns TestData.member(groupId, member)
    }

    @Test
    fun `should refuse to list events when the caller is not a member`() {
        val ex = assertThrows<BusinessException> { service.getEvents(groupId, outsider) }

        assertEquals(ErrorCode.GROUP_NOT_MEMBER, ex.errorCode)
        verify(exactly = 0) { events.findByConversationId(any()) }
    }

    @Test
    fun `should refuse to list RSVPs when the caller is not a member`() {
        val event = GroupEvent(conversationId = groupId, createdBy = member, title = "t", eventTime = Instant.now())
        every { events.findById(event.id) } returns event

        val ex = assertThrows<BusinessException> { service.getRsvps(groupId, event.id, outsider) }

        assertEquals(ErrorCode.GROUP_NOT_MEMBER, ex.errorCode)
        verify(exactly = 0) { events.findRsvpsByEventId(any()) }
    }

    @Test
    fun `should refuse to list RSVPs when the event belongs to another conversation`() {
        val elsewhere = GroupEvent(
            conversationId = UUID.randomUUID(), createdBy = member, title = "t", eventTime = Instant.now()
        )
        every { events.findById(elsewhere.id) } returns elsewhere

        val ex = assertThrows<BusinessException> { service.getRsvps(groupId, elsewhere.id, member) }

        assertEquals(ErrorCode.EVENT_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should list RSVPs when the caller is a member`() {
        val event = GroupEvent(conversationId = groupId, createdBy = member, title = "t", eventTime = Instant.now())
        every { events.findById(event.id) } returns event
        every { events.findRsvpsByEventId(event.id) } returns emptyList()

        assertEquals(emptyList<Any>(), service.getRsvps(groupId, event.id, member))
    }
}

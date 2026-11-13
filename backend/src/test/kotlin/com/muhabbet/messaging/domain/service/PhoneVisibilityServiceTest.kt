package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.TestData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The phone-disclosure rule (#902) in isolation. The query behind
 * `findDirectCounterpartsWhoDisclosed` is pinned against Postgres in
 * `ParticipantPhoneVisibilityIntegrationTest`; this pins what the service does with its answer.
 */
class PhoneVisibilityServiceTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var blockPolicy: BlockPolicyPort
    private lateinit var service: PhoneVisibilityService

    private val me = TestData.USER_ID_1
    private val dmPartner = TestData.USER_ID_2
    private val groupOnly = TestData.USER_ID_3

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk()
        blockPolicy = mockk()
        service = PhoneVisibilityService(conversationRepository, blockPolicy)
        every { blockPolicy.findBlockedBy(any(), any()) } returns emptySet()
    }

    @Test
    fun `should disclose own number and DM partner's but not a group-only co-member's`() {
        every {
            conversationRepository.findDirectCounterpartsWhoDisclosed(me, setOf(dmPartner, groupOnly))
        } returns setOf(dmPartner)

        val visible = service.phoneVisibleParticipants(me, listOf(me, dmPartner, groupOnly))

        assertEquals(setOf(me, dmPartner), visible)
    }

    @Test
    fun `should withhold the number of a DM partner who has blocked the viewer`() {
        every { conversationRepository.findDirectCounterpartsWhoDisclosed(me, any()) } returns setOf(dmPartner)
        every { blockPolicy.findBlockedBy(me, setOf(dmPartner)) } returns setOf(dmPartner)

        assertEquals(setOf(me), service.phoneVisibleParticipants(me, listOf(me, dmPartner)))
    }

    @Test
    fun `should not include the viewer when the viewer is not among the participants`() {
        every { conversationRepository.findDirectCounterpartsWhoDisclosed(me, any()) } returns emptySet()

        assertEquals(emptySet<Any>(), service.phoneVisibleParticipants(me, listOf(dmPartner)))
    }

    @Test
    fun `should query nothing when the viewer is the only participant`() {
        assertEquals(setOf(me), service.phoneVisibleParticipants(me, listOf(me)))

        verify(exactly = 0) { conversationRepository.findDirectCounterpartsWhoDisclosed(any(), any()) }
        verify(exactly = 0) { blockPolicy.findBlockedBy(any(), any()) }
    }

    @Test
    fun `should not consult blocks when nobody disclosed`() {
        every { conversationRepository.findDirectCounterpartsWhoDisclosed(me, any()) } returns emptySet()

        assertEquals(setOf(me), service.phoneVisibleParticipants(me, listOf(me, groupOnly)))
        verify(exactly = 0) { blockPolicy.findBlockedBy(any(), any()) }
    }
}

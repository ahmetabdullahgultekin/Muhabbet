package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class ChannelServiceTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var service: ChannelService

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk(relaxed = true)
        service = ChannelService(conversationRepository)
    }

    @Test
    fun `should resolve every channel's members in a single batched query`() {
        val channels = (1..3).map {
            Conversation(type = ConversationType.CHANNEL, name = "Channel $it")
        }
        every { conversationRepository.findByType(ConversationType.CHANNEL) } returns channels
        every { conversationRepository.findMembersByConversationIds(channels.map { it.id }) } returns
            channels.associate { conv ->
                conv.id to listOf(ConversationMember(conversationId = conv.id, userId = otherUserId))
            }

        service.listChannels(userId)

        verify(exactly = 1) { conversationRepository.findMembersByConversationIds(any()) }
        // The per-channel lookup is the N+1 this replaced — one query per channel listed.
        verify(exactly = 0) { conversationRepository.findMembersByConversationId(any()) }
    }

    @Test
    fun `should report subscriber count and the caller's own subscription`() {
        val subscribed = Conversation(type = ConversationType.CHANNEL, name = "News")
        val notSubscribed = Conversation(type = ConversationType.CHANNEL, name = "Tech")

        every { conversationRepository.findByType(ConversationType.CHANNEL) } returns
            listOf(subscribed, notSubscribed)
        every { conversationRepository.findMembersByConversationIds(any()) } returns mapOf(
            subscribed.id to listOf(
                ConversationMember(conversationId = subscribed.id, userId = userId),
                ConversationMember(conversationId = subscribed.id, userId = otherUserId)
            ),
            notSubscribed.id to listOf(
                ConversationMember(conversationId = notSubscribed.id, userId = otherUserId)
            )
        )

        val result = service.listChannels(userId)

        assertEquals(2, result.size)
        assertEquals(2, result[0].subscriberCount)
        assertTrue(result[0].isSubscribed)
        assertEquals(1, result[1].subscriberCount)
        assertFalse(result[1].isSubscribed)
    }

    @Test
    fun `should report an empty channel as having no members rather than dropping it`() {
        val channel = Conversation(type = ConversationType.CHANNEL, name = "Empty")
        every { conversationRepository.findByType(ConversationType.CHANNEL) } returns listOf(channel)
        // A channel with no rows is simply absent from the batch result.
        every { conversationRepository.findMembersByConversationIds(any()) } returns emptyMap()

        val result = service.listChannels(userId)

        assertEquals(1, result.size)
        assertEquals(0, result[0].subscriberCount)
        assertFalse(result[0].isSubscribed)
    }

    @Test
    fun `should not query members at all when there are no channels`() {
        every { conversationRepository.findByType(ConversationType.CHANNEL) } returns emptyList()

        assertTrue(service.listChannels(userId).isEmpty())

        verify(exactly = 0) { conversationRepository.findMembersByConversationIds(any()) }
    }
}

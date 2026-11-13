package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.TestData
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A channel's subscribers are not each other's business. The chat list shows a channel with the
 * viewer and the people who run it, never the other subscribers — anyone can subscribe, so listing
 * them would hand every subscriber's identity to anyone who asked.
 */
class ChannelSubscriberListTest {

    @Test
    fun `should list only the viewer and the channel's admins when the conversation is a channel`() {
        val me = TestData.USER_ID_1
        val owner = TestData.USER_ID_2
        val other = TestData.USER_ID_3
        val channel = TestData.channelConversation(createdBy = owner)
        val conversations = mockk<ConversationRepository>(relaxed = true)
        every { conversations.findConversationsByUserId(me) } returns listOf(channel)
        every { conversations.findMembersByConversationIds(listOf(channel.id)) } returns mapOf(
            channel.id to listOf(
                TestData.member(channel.id, me),
                TestData.member(channel.id, owner, role = MemberRole.OWNER),
                TestData.member(channel.id, other)
            )
        )
        val service = ConversationService(conversations, mockk(relaxed = true), mockk(), mockk(relaxed = true))

        val summary = service.getConversations(me, null, 20).items.single()

        assertEquals(setOf(me, owner), summary.participantIds.toSet())
    }
}

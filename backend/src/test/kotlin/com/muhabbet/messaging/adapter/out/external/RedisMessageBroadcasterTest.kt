package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant
import java.util.UUID

/**
 * #618: a push must be suppressed only for a recipient foregrounded on THIS exact conversation, not
 * merely for one with a socket open somewhere. Before this fix, [WebSocketSessionManager.isOnline]
 * alone decided whether [OfflinePushSender] ran at all — a recipient reading a different chat, or
 * with the screen off but the socket still up, got neither a live update they'd notice nor a push.
 */
class RedisMessageBroadcasterTest {

    private val sessionManager: WebSocketSessionManager = mockk(relaxed = true)
    private val redisTemplate: StringRedisTemplate = mockk(relaxed = true)
    private val userDirectory: UserDirectoryPort = mockk(relaxed = true)
    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val offlinePushSender: OfflinePushSender = mockk(relaxed = true)

    private lateinit var broadcaster: RedisMessageBroadcaster

    private val conversationId = UUID.randomUUID()
    private val recipientId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        broadcaster = RedisMessageBroadcaster(
            sessionManager, redisTemplate, userDirectory, conversationRepository, offlinePushSender
        )
        every { userDirectory.findDisplayInfo(any()) } returns emptyMap()
        every { conversationRepository.findById(conversationId) } returns null
    }

    private fun message() = Message(
        id = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = UUID.randomUUID(),
        contentType = ContentType.TEXT,
        content = "merhaba",
        clientTimestamp = Instant.now()
    )

    @Nested
    inner class PushSuppression {

        @Test
        fun `should not push a recipient who is foregrounded on this exact conversation`() {
            every { sessionManager.isOnline(recipientId) } returns true
            every { sessionManager.isViewingConversation(recipientId, conversationId) } returns true

            broadcaster.broadcastMessage(message(), listOf(recipientId))

            verify(exactly = 0) { offlinePushSender.sendTo(recipientId, any(), any(), any()) }
        }

        @Test
        fun `should push a recipient who is online but viewing a different conversation`() {
            // The exact #618 shape: a live socket, but on another screen.
            every { sessionManager.isOnline(recipientId) } returns true
            every { sessionManager.isViewingConversation(recipientId, conversationId) } returns false

            broadcaster.broadcastMessage(message(), listOf(recipientId))

            verify { sessionManager.sendToUser(recipientId, any()) }
            verify { offlinePushSender.sendTo(recipientId, any(), any(), any()) }
        }

        @Test
        fun `should push a recipient who is online and viewing nothing at all`() {
            every { sessionManager.isOnline(recipientId) } returns true
            every { sessionManager.isViewingConversation(recipientId, any()) } returns false

            broadcaster.broadcastMessage(message(), listOf(recipientId))

            verify { offlinePushSender.sendTo(recipientId, any(), any(), any()) }
        }

        @Test
        fun `should still push a recipient with no session at all, as before`() {
            every { sessionManager.isOnline(recipientId) } returns false
            every { sessionManager.isViewingConversation(recipientId, any()) } returns false

            broadcaster.broadcastMessage(message(), listOf(recipientId))

            verify(exactly = 0) { sessionManager.sendToUser(recipientId, any()) }
            verify { redisTemplate.convertAndSend("${RedisMessageBroadcaster.WS_CHANNEL_PREFIX}$recipientId", any()) }
            verify { offlinePushSender.sendTo(recipientId, any(), any(), any()) }
        }
    }
}

package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.adapter.out.WebSocketMessageBroadcaster
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant
import java.util.UUID

/**
 * #618: a push must be withheld only for a recipient foregrounded on THIS exact conversation right
 * now — not merely for one with a socket open somewhere. Run as one parameterized suite over both
 * [MessageBroadcaster] implementations for the same reason [MutedConversationSuppressesPushTest]
 * is: #469 is this exact push-fan-out block existing twice and drifting apart once already, so
 * fixing one and trusting the other by inspection is how that happens again.
 */
class ConversationFocusSuppressesPushTest {

    private class Fixture(val broadcaster: MessageBroadcaster) {
        lateinit var sessionManager: WebSocketSessionManager
        lateinit var offlinePushSender: OfflinePushSender
    }

    private fun webSocketFixture(): Fixture {
        val sessionManager: WebSocketSessionManager = mockk(relaxed = true)
        val userDirectory: UserDirectoryPort = mockk(relaxed = true)
        val conversationRepository: ConversationRepository = mockk(relaxed = true)
        val offlinePushSender: OfflinePushSender = mockk(relaxed = true)
        val broadcaster = WebSocketMessageBroadcaster(
            sessionManager = sessionManager,
            userDirectory = userDirectory,
            conversationRepository = conversationRepository,
            offlinePushSender = offlinePushSender
        )
        return Fixture(broadcaster).apply {
            this.sessionManager = sessionManager
            this.offlinePushSender = offlinePushSender
        }
    }

    private fun redisFixture(): Fixture {
        val sessionManager: WebSocketSessionManager = mockk(relaxed = true)
        val redisTemplate: StringRedisTemplate = mockk(relaxed = true)
        val userDirectory: UserDirectoryPort = mockk(relaxed = true)
        val conversationRepository: ConversationRepository = mockk(relaxed = true)
        val offlinePushSender: OfflinePushSender = mockk(relaxed = true)
        val broadcaster = RedisMessageBroadcaster(
            sessionManager = sessionManager,
            redisTemplate = redisTemplate,
            userDirectory = userDirectory,
            conversationRepository = conversationRepository,
            offlinePushSender = offlinePushSender
        )
        return Fixture(broadcaster).apply {
            this.sessionManager = sessionManager
            this.offlinePushSender = offlinePushSender
        }
    }

    private val fixtures: Map<String, () -> Fixture> = linkedMapOf(
        "WebSocketMessageBroadcaster" to ::webSocketFixture,
        "RedisMessageBroadcaster" to ::redisFixture
    )

    private fun textMessage(conversationId: UUID) = Message(
        id = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = UUID.randomUUID(),
        contentType = ContentType.TEXT,
        content = "hello",
        clientTimestamp = Instant.now(),
        serverTimestamp = Instant.now()
    )

    @TestFactory
    fun `should not push a recipient foregrounded on this exact conversation`(): List<DynamicTest> =
        fixtures.map { (name, make) ->
            dynamicTest(name) {
                val f = make()
                val conversationId = UUID.randomUUID()
                val message = textMessage(conversationId)
                val recipient = UUID.randomUUID()
                val member = ConversationMember(conversationId = conversationId, userId = recipient)
                every { f.sessionManager.isOnline(recipient) } returns true
                every { f.sessionManager.isViewingConversation(recipient, conversationId) } returns true

                f.broadcaster.broadcastMessage(message, listOf(member))

                verify(exactly = 0) { f.offlinePushSender.sendTo(recipient, any(), any(), any()) }
            }
        }

    @TestFactory
    fun `should push a recipient who is online but viewing a different conversation`(): List<DynamicTest> =
        fixtures.map { (name, make) ->
            dynamicTest(name) {
                // The exact #618 shape: a live socket, but on another screen. Before the fix this
                // whole branch was unreachable once a recipient was online at all.
                val f = make()
                val conversationId = UUID.randomUUID()
                val message = textMessage(conversationId)
                val recipient = UUID.randomUUID()
                val member = ConversationMember(conversationId = conversationId, userId = recipient)
                every { f.sessionManager.isOnline(recipient) } returns true
                every { f.sessionManager.isViewingConversation(recipient, conversationId) } returns false

                f.broadcaster.broadcastMessage(message, listOf(member))

                verify { f.sessionManager.sendToUser(recipient, any()) }
                verify(exactly = 1) { f.offlinePushSender.sendTo(recipient, message, any(), any()) }
            }
        }

    @TestFactory
    fun `should push an offline recipient exactly as before`(): List<DynamicTest> =
        fixtures.map { (name, make) ->
            dynamicTest(name) {
                val f = make()
                val conversationId = UUID.randomUUID()
                val message = textMessage(conversationId)
                val recipient = UUID.randomUUID()
                val member = ConversationMember(conversationId = conversationId, userId = recipient)
                every { f.sessionManager.isOnline(recipient) } returns false
                every { f.sessionManager.isViewingConversation(recipient, any()) } returns false

                f.broadcaster.broadcastMessage(message, listOf(member))

                verify(exactly = 1) { f.offlinePushSender.sendTo(recipient, message, any(), any()) }
            }
        }
}

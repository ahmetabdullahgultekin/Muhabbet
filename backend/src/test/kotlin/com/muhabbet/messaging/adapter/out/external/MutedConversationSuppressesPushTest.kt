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
 * #571: muting a conversation persisted a `mutedUntil` and drew an icon in the list, but nothing
 * on the push path ever asked for it — `OfflinePushSender` fanned out to every offline recipient
 * regardless. The fix put the check in both live [MessageBroadcaster] implementations, at the
 * point each already decides "this recipient is offline, so send a push": [RedisMessageBroadcaster]
 * (the `@Primary` bean actually running in prod) and [WebSocketMessageBroadcaster] (its
 * single-instance sibling, in `NoOpMessageBroadcaster.kt`).
 *
 * Run as one parameterized suite over both implementations — #469's own history is that this exact
 * push-fan-out block existed twice and drifted apart once already. Testing one and trusting the
 * other by inspection is how that happened.
 */
class MutedConversationSuppressesPushTest {

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
    fun `should not push to an offline recipient who currently has the conversation muted`(): List<DynamicTest> =
        fixtures.map { (name, make) ->
            dynamicTest(name) {
                val f = make()
                val conversationId = UUID.randomUUID()
                val message = textMessage(conversationId)
                val recipient = UUID.randomUUID()
                val muted = ConversationMember(
                    conversationId = conversationId,
                    userId = recipient,
                    mutedUntil = Instant.now().plusSeconds(8 * 3600)
                )
                every { f.sessionManager.isOnline(recipient) } returns false

                f.broadcaster.broadcastMessage(message, listOf(muted))

                verify(exactly = 0) { f.offlinePushSender.sendTo(recipient, any(), any(), any()) }
            }
        }

    @TestFactory
    fun `should push again once an expired mute has lapsed`(): List<DynamicTest> =
        fixtures.map { (name, make) ->
            dynamicTest(name) {
                val f = make()
                val conversationId = UUID.randomUUID()
                val message = textMessage(conversationId)
                val recipient = UUID.randomUUID()
                val expiredMute = ConversationMember(
                    conversationId = conversationId,
                    userId = recipient,
                    mutedUntil = Instant.now().minusSeconds(60)
                )
                every { f.sessionManager.isOnline(recipient) } returns false

                f.broadcaster.broadcastMessage(message, listOf(expiredMute))

                verify(exactly = 1) { f.offlinePushSender.sendTo(recipient, message, any(), any()) }
            }
        }

    @TestFactory
    fun `should push an offline recipient who never muted the conversation`(): List<DynamicTest> =
        fixtures.map { (name, make) ->
            dynamicTest(name) {
                val f = make()
                val conversationId = UUID.randomUUID()
                val message = textMessage(conversationId)
                val recipient = UUID.randomUUID()
                val unmuted = ConversationMember(conversationId = conversationId, userId = recipient, mutedUntil = null)
                every { f.sessionManager.isOnline(recipient) } returns false

                f.broadcaster.broadcastMessage(message, listOf(unmuted))

                verify(exactly = 1) { f.offlinePushSender.sendTo(recipient, message, any(), any()) }
            }
        }

    @TestFactory
    fun `muting for one member must not silence the push to another offline member of the same conversation`(): List<DynamicTest> =
        fixtures.map { (name, make) ->
            dynamicTest(name) {
                val f = make()
                val conversationId = UUID.randomUUID()
                val message = textMessage(conversationId)
                val mutedRecipient = UUID.randomUUID()
                val unmutedRecipient = UUID.randomUUID()
                val members = listOf(
                    ConversationMember(
                        conversationId = conversationId,
                        userId = mutedRecipient,
                        mutedUntil = Instant.now().plusSeconds(3600)
                    ),
                    ConversationMember(conversationId = conversationId, userId = unmutedRecipient, mutedUntil = null)
                )
                every { f.sessionManager.isOnline(any()) } returns false

                f.broadcaster.broadcastMessage(message, members)

                verify(exactly = 0) { f.offlinePushSender.sendTo(mutedRecipient, any(), any(), any()) }
                verify(exactly = 1) { f.offlinePushSender.sendTo(unmutedRecipient, message, any(), any()) }
            }
        }

    @TestFactory
    fun `a muted recipient who is online still gets the message over the socket`(): List<DynamicTest> =
        fixtures.map { (name, make) ->
            dynamicTest(name) {
                // Muting withholds only the courtesy push, never the message itself: still
                // delivered, still counted unread, still in the chat when they open it.
                val f = make()
                val conversationId = UUID.randomUUID()
                val message = textMessage(conversationId)
                val recipient = UUID.randomUUID()
                val muted = ConversationMember(
                    conversationId = conversationId,
                    userId = recipient,
                    mutedUntil = Instant.now().plusSeconds(3600)
                )
                every { f.sessionManager.isOnline(recipient) } returns true

                f.broadcaster.broadcastMessage(message, listOf(muted))

                verify { f.sessionManager.sendToUser(recipient, any()) }
                verify(exactly = 0) { f.offlinePushSender.sendTo(any(), any(), any(), any()) }
            }
        }
}

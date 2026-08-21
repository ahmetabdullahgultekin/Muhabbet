package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.adapter.out.WebSocketMessageBroadcaster
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.service.PushNotificationComposer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant
import java.util.UUID

/**
 * #492 — the push fan-out reads every recipient's devices in one query, not one query per
 * recipient.
 *
 * The old shape called `deviceRepository.findByUserId` from inside the loop that walks the
 * recipients, so a group of two hundred issued two hundred statements to send one message — each of
 * them inside the send transaction (#491), holding a pool connection while it ran.
 *
 * Run against **both** `MessageBroadcaster` implementations, in the house style of
 * [ConversationFocusSuppressesPushTest]: #469 was this same pair of broadcasters drifting apart, so
 * a fix applied to one of them is not a fix.
 */
class BatchedDeviceLookupTest {

    private class Fixture(
        val name: String,
        val broadcaster: MessageBroadcaster,
        val deviceRepository: DeviceRepository
    )

    private val conversationId = UUID.randomUUID()
    private val senderId = UUID.randomUUID()
    private val recipients = List(3) { UUID.randomUUID() }

    private fun fixtures(): List<Fixture> {
        fun parts(): Triple<WebSocketSessionManager, DeviceRepository, OfflinePushSender> {
            val sessionManager: WebSocketSessionManager = mockk(relaxed = true)
            // Nobody is online and nobody is looking at the chat, so every recipient is owed a push.
            every { sessionManager.isOnline(any()) } returns false
            every { sessionManager.isViewingConversation(any(), any()) } returns false

            val deviceRepository: DeviceRepository = mockk()
            every { deviceRepository.findByUserIdIn(any()) } returns recipients.map {
                Device(userId = it, platform = "android", pushToken = "token-$it", locale = "tr")
            }
            val pushPort: PushNotificationPort = mockk(relaxed = true)
            val composer: PushNotificationComposer = mockk(relaxed = true)
            return Triple(sessionManager, deviceRepository, OfflinePushSender(deviceRepository, pushPort, composer))
        }

        val (redisSessions, redisDevices, redisPush) = parts()
        val (wsSessions, wsDevices, wsPush) = parts()
        val conversationRepository: ConversationRepository = mockk(relaxed = true)
        every { conversationRepository.findById(conversationId) } returns
            Conversation(id = conversationId, type = ConversationType.GROUP)
        val userDirectory: UserDirectoryPort = mockk(relaxed = true)

        return listOf(
            Fixture(
                "RedisMessageBroadcaster",
                RedisMessageBroadcaster(
                    redisSessions, mockk<StringRedisTemplate>(relaxed = true),
                    userDirectory, conversationRepository, redisPush
                ),
                redisDevices
            ),
            Fixture(
                "WebSocketMessageBroadcaster",
                WebSocketMessageBroadcaster(wsSessions, userDirectory, conversationRepository, wsPush),
                wsDevices
            )
        )
    }

    private fun message() = Message(
        id = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = senderId,
        contentType = ContentType.TEXT,
        content = "merhaba",
        clientTimestamp = Instant.now()
    )

    private fun members() = recipients.map { ConversationMember(conversationId = conversationId, userId = it) }

    @TestFactory
    fun `should read the device rows once for the whole group when a message is broadcast`() =
        fixtures().map { f ->
            DynamicTest.dynamicTest(f.name) {
                f.broadcaster.broadcastMessage(message(), members())

                verify(exactly = 1) {
                    f.deviceRepository.findByUserIdIn(match { it.toSet() == recipients.toSet() })
                }
            }
        }

    @TestFactory
    fun `should not query for devices at all when nobody is owed a push`() =
        fixtures().map { f ->
            DynamicTest.dynamicTest(f.name) {
                // An empty recipient list is the cheap proof that the query is not unconditional;
                // the muted and focused cases are covered by their own suites.
                f.broadcaster.broadcastMessage(message(), emptyList())

                verify(exactly = 0) { f.deviceRepository.findByUserIdIn(any()) }
            }
        }
}
